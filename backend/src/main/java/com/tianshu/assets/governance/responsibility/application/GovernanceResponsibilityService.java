package com.tianshu.assets.governance.responsibility.application;

import com.tianshu.assets.governance.acceptance.application.GovernanceAcceptanceStore;
import com.tianshu.assets.governance.acceptance.domain.GovernanceAcceptanceRound;
import com.tianshu.assets.governance.application.GovernanceValidationException;
import com.tianshu.assets.governance.issue.application.GovernanceIssueStore;
import com.tianshu.assets.governance.issue.domain.GovernanceIssueStatus;
import com.tianshu.assets.governance.scan.application.GovernanceScanRunStore;
import com.tianshu.assets.governance.task.application.GovernanceEmployeeDirectory;
import com.tianshu.assets.governance.task.application.GovernanceTaskStore;
import com.tianshu.assets.governance.task.domain.GovernanceTask;
import com.tianshu.assets.governance.task.domain.GovernanceTaskStatus;
import com.tianshu.assets.governance.task.domain.GovernanceWorkflowVersion;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 组织与责任看板（R4）：部门视角看"谁名下多少待办/逾期"与月度治理复盘。
 *
 * <ul>
 *   <li>员工分桶（实时存量，口径与「我的待办」一致）：整改中/待确认/待验收按任务状态归类，
 *       已逾期按截止日早于今天（与前三列重叠统计）；已升级=闭环任务逾期满 3 天；
 *       已完成=累计完成任务。</li>
 *   <li>月度复盘（按 YYYY-MM 切片，时间取自各事实的创建/完成时刻）：
 *       本月扫描运行与核验、本月新发现问题、本月验收通过（闭环完成）、
 *       当前开放问题/逾期/升级存量，并附上月扫描与新发现问题供环比。</li>
 * </ul>
 * 历史（LEGACY）只读任务按负责人计入名下分桶与逾期，但不进入"已升级"链路（与通知升级一致）。
 */
@Service("governanceResponsibilityBoardService")
public class GovernanceResponsibilityService {

    private static final int OVERDUE_ESCALATION_DAYS = 3;

    private final GovernanceTaskStore tasks;
    private final GovernanceEmployeeDirectory employees;
    private final GovernanceIssueStore issues;
    private final GovernanceScanRunStore scans;
    private final GovernanceAcceptanceStore acceptance;

    @Autowired
    public GovernanceResponsibilityService(
            GovernanceTaskStore tasks,
            GovernanceEmployeeDirectory employees,
            GovernanceIssueStore issues,
            GovernanceScanRunStore scans,
            GovernanceAcceptanceStore acceptance) {
        this.tasks = tasks;
        this.employees = employees;
        this.issues = issues;
        this.scans = scans;
        this.acceptance = acceptance;
    }

    /** @param month YYYY-MM，为空时取当前月 */
    public BoardView board(String month) {
        var targetMonth = parseMonth(month);
        var today = LocalDate.now();
        var allTasks = tasks.findAll();
        var people = new ArrayList<EmployeeRow>();
        var totals = new EmployeeRow("", "合计", "", 0, 0, 0, 0, 0, 0, 0);

        for (var employee : employees.findAllEmployees()) {
            var row = new EmployeeRow(employee.id(), employee.name(), employee.department(), 0, 0, 0, 0, 0, 0, 0);
            for (var task : allTasks) {
                if (!employee.id().equals(task.ownerUserId())) continue;
                row = row.accumulate(task, today);
            }
            people.add(row);
            totals = totals.add(row);
        }
        people.sort(Comparator
                .comparingInt(EmployeeRow::overdue).reversed()
                .thenComparing(Comparator.comparing(EmployeeRow::name)));

        return new BoardView(
                targetMonth.toString(),
                Instant.now(),
                people,
                totals,
                monthlyReview(targetMonth, today, allTasks));
    }

    private ReviewSummary monthlyReview(YearMonth month, LocalDate today, List<GovernanceTask> allTasks) {
        var zone = ZoneId.systemDefault();
        var from = month.atDay(1).atStartOfDay(zone).toInstant();
        var toExclusive = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant();
        var prevMonth = month.minusMonths(1);
        var prevFrom = prevMonth.atDay(1).atStartOfDay(zone).toInstant();
        var prevToExclusive = month.atDay(1).atStartOfDay(zone).toInstant();

        var scanRuns = 0;
        var scannedAssets = 0;
        var prevScanRuns = 0;
        var prevScannedAssets = 0;
        for (var run : scans.findAll()) {
            if (within(run.startedAt(), from, toExclusive)) {
                scanRuns++;
                scannedAssets += run.scannedAssetCount();
            }
            if (within(run.startedAt(), prevFrom, prevToExclusive)) {
                prevScanRuns++;
                prevScannedAssets += run.scannedAssetCount();
            }
        }

        var newIssues = 0;
        var prevNewIssues = 0;
        var openIssues = 0;
        for (var issue : issues.find(null, null, null)) {
            if (issue.status() == GovernanceIssueStatus.OPEN) openIssues++;
            if (within(issue.createdAt(), from, toExclusive)) newIssues++;
            else if (within(issue.createdAt(), prevFrom, prevToExclusive)) prevNewIssues++;
        }

        var closedTaskIds = new HashSet<Long>();
        var prevClosedTaskIds = new HashSet<Long>();
        var overdueTasks = 0;
        var escalatedTasks = 0;
        for (var task : allTasks) {
            if (task.status() != GovernanceTaskStatus.COMPLETED
                    && task.dueDate() != null && task.dueDate().isBefore(today)) {
                overdueTasks++;
                if (task.workflowVersion() == GovernanceWorkflowVersion.CLOSED_LOOP_V1
                        && overdueDays(task.dueDate(), today) >= OVERDUE_ESCALATION_DAYS) {
                    escalatedTasks++;
                }
            }
            if (task.workflowVersion() != GovernanceWorkflowVersion.CLOSED_LOOP_V1) continue;
            var rounds = acceptance.rounds(task.id());
            if (passedAcceptanceBetween(rounds, from, toExclusive)) closedTaskIds.add(task.id());
            if (passedAcceptanceBetween(rounds, prevFrom, prevToExclusive)) prevClosedTaskIds.add(task.id());
        }

        return new ReviewSummary(
                month.toString(),
                prevMonth.toString(),
                scanRuns,
                scannedAssets,
                newIssues,
                closedTaskIds.size(),
                openIssues,
                overdueTasks,
                escalatedTasks,
                prevScanRuns,
                prevScannedAssets,
                prevNewIssues,
                prevClosedTaskIds.size());
    }

    private boolean passedAcceptanceBetween(List<GovernanceAcceptanceRound> rounds, Instant from, Instant toExclusive) {
        for (var round : rounds) {
            if (round.status() == GovernanceAcceptanceRound.Status.PASSED
                    && round.completedAt() != null
                    && within(round.completedAt(), from, toExclusive)) {
                return true;
            }
        }
        return false;
    }

    private static boolean within(Instant value, Instant from, Instant toExclusive) {
        return value != null && !value.isBefore(from) && value.isBefore(toExclusive);
    }

    /** 已逾期天数：截止日早于今天为正（逾期），晚于今天为负。 */
    private static long overdueDays(LocalDate dueDate, LocalDate today) {
        return ChronoUnit.DAYS.between(dueDate, today);
    }

    private static YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) return YearMonth.now();
        try {
            return YearMonth.parse(month);
        } catch (java.time.format.DateTimeParseException exception) {
            throw new GovernanceValidationException("复盘月份格式应为 YYYY-MM，例如 2026-09");
        }
    }

    public record EmployeeRow(
            String userId,
            String name,
            String department,
            int executing,
            int confirming,
            int accepting,
            int overdue,
            int escalated,
            int completed,
            int totalAssigned) {

        /** 把单个任务累加进该员工的分桶（逾期与整改中/待确认/待验收允许重叠统计）。 */
        public EmployeeRow accumulate(GovernanceTask task, LocalDate today) {
            var open = task.status() != GovernanceTaskStatus.COMPLETED;
            var executing = this.executing + (!open ? 0
                    : task.status() == GovernanceTaskStatus.DRAFT
                            || task.status() == GovernanceTaskStatus.IN_PROGRESS
                            || task.status() == GovernanceTaskStatus.REWORK_REQUIRED ? 1 : 0);
            var confirming = this.confirming + (task.status() == GovernanceTaskStatus.PENDING_CONFIRMATION ? 1 : 0);
            var accepting = this.accepting + (task.status() == GovernanceTaskStatus.PENDING_ACCEPTANCE ? 1 : 0);
            var completed = this.completed + (task.status() == GovernanceTaskStatus.COMPLETED ? 1 : 0);
            var overdue = this.overdue + (open && task.dueDate() != null && task.dueDate().isBefore(today) ? 1 : 0);
            var escalated = this.escalated + (open && task.workflowVersion() == GovernanceWorkflowVersion.CLOSED_LOOP_V1
                    && task.dueDate() != null && overdueDays(task.dueDate(), today) >= OVERDUE_ESCALATION_DAYS ? 1 : 0);
            return new EmployeeRow(userId, name, department,
                    executing, confirming, accepting, overdue, escalated, completed,
                    executing + confirming + accepting + completed);
        }

        public EmployeeRow add(EmployeeRow other) {
            return new EmployeeRow(
                    "", "合计", "",
                    executing + other.executing,
                    confirming + other.confirming,
                    accepting + other.accepting,
                    overdue + other.overdue,
                    escalated + other.escalated,
                    completed + other.completed,
                    totalAssigned + other.totalAssigned);
        }
    }

    public record ReviewSummary(
            String month,
            String prevMonth,
            int scanRuns,
            int scannedAssets,
            int newIssues,
            int closedTasks,
            int openIssues,
            int overdueTasks,
            int escalatedTasks,
            int prevScanRuns,
            int prevScannedAssets,
            int prevNewIssues,
            int prevClosedTasks) {}

    public record BoardView(
            String month,
            Instant generatedAt,
            List<EmployeeRow> employees,
            EmployeeRow totals,
            ReviewSummary monthly) {}
}
