package com.tianshu.assets.governance.responsibility.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.tianshu.assets.governance.acceptance.application.GovernanceAcceptanceStore;
import com.tianshu.assets.governance.acceptance.domain.GovernanceAcceptanceMetricResult;
import com.tianshu.assets.governance.acceptance.domain.GovernanceAcceptanceRound;
import com.tianshu.assets.governance.acceptance.domain.GovernanceQualityMetric;
import com.tianshu.assets.governance.acceptance.domain.GovernanceQualityPolicySnapshot;
import com.tianshu.assets.governance.application.GovernanceValidationException;
import com.tianshu.assets.governance.infrastructure.InMemoryGovernanceAcceptanceStore;
import com.tianshu.assets.governance.infrastructure.InMemoryGovernanceEmployeeDirectory;
import com.tianshu.assets.governance.infrastructure.InMemoryGovernanceIssueStore;
import com.tianshu.assets.governance.infrastructure.InMemoryGovernanceScanRunStore;
import com.tianshu.assets.governance.infrastructure.InMemoryGovernanceTaskStore;
import com.tianshu.assets.governance.issue.domain.GovernanceField;
import com.tianshu.assets.governance.issue.domain.GovernanceIssue;
import com.tianshu.assets.governance.issue.domain.GovernanceIssueStatus;
import com.tianshu.assets.governance.scan.application.GovernanceScanRunStore.Counts;
import com.tianshu.assets.governance.scan.domain.GovernanceScanRun;
import com.tianshu.assets.governance.scan.domain.GovernanceScanRunStatus;
import com.tianshu.assets.governance.scan.domain.GovernanceScanTriggerType;
import com.tianshu.assets.governance.task.domain.GovernanceTask;
import com.tianshu.assets.governance.task.domain.GovernanceTaskStatus;
import com.tianshu.assets.governance.task.domain.GovernanceWorkflowVersion;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GovernanceResponsibilityServiceTest {

    private final ZoneId zone = ZoneId.systemDefault();

    private InMemoryGovernanceTaskStore taskStore;
    private InMemoryGovernanceScanRunStore scanStore;
    private InMemoryGovernanceIssueStore issueStore;
    private InMemoryGovernanceAcceptanceStore acceptanceStore;
    private GovernanceResponsibilityService service;

    @BeforeEach
    void setUp() {
        taskStore = new InMemoryGovernanceTaskStore();
        scanStore = new InMemoryGovernanceScanRunStore();
        issueStore = new InMemoryGovernanceIssueStore();
        acceptanceStore = new InMemoryGovernanceAcceptanceStore();
        service = new GovernanceResponsibilityService(
                taskStore,
                new InMemoryGovernanceEmployeeDirectory(),
                issueStore,
                scanStore,
                acceptanceStore);
    }

    private GovernanceTask closedLoopTask(
            long id, String ownerUserId, String ownerName, LocalDate dueDate, GovernanceTaskStatus status) {
        return new GovernanceTask(id, "GOV-R4-" + id, "看板任务 " + id, "FIELD_SUPPLEMENT", "FIELD_COMPLETENESS",
                ownerUserId, ownerName, ownerUserId, dueDate, status, 1,
                GovernanceWorkflowVersion.CLOSED_LOOP_V1, null, null, 0, 0, 0);
    }

    private GovernanceTask legacyTask(long id, String ownerUserId, String ownerName,
            LocalDate dueDate, GovernanceTaskStatus status) {
        return new GovernanceTask(id, "GOV-R4L-" + id, "历史任务 " + id, "LEGACY_MANUAL_PROGRESS", "历史整理",
                ownerUserId, ownerName, ownerUserId, dueDate, status, 0,
                GovernanceWorkflowVersion.LEGACY_PROGRESS, null, null, 10, 3, 0);
    }

    @Test
    void groupsTasksByResponsibleEmployeeWithOverlappingOverdueAndEscalation() {
        var today = LocalDate.now();
        taskStore.insert(closedLoopTask(1, "emp-li", "李工", today.plusDays(5), GovernanceTaskStatus.IN_PROGRESS));
        taskStore.insert(closedLoopTask(2, "emp-li", "李工", today.minusDays(1), GovernanceTaskStatus.PENDING_CONFIRMATION));
        taskStore.insert(closedLoopTask(3, "emp-li", "李工", today.minusDays(5), GovernanceTaskStatus.PENDING_ACCEPTANCE));
        taskStore.insert(closedLoopTask(4, "emp-li", "李工", today.minusDays(9), GovernanceTaskStatus.COMPLETED));
        taskStore.insert(legacyTask(5, "emp-li", "李工", today.minusDays(40), GovernanceTaskStatus.IN_PROGRESS));
        taskStore.insert(closedLoopTask(6, "emp-chen", "陈工", today.plusDays(10), GovernanceTaskStatus.DRAFT));

        var board = service.board(null);

        var li = board.employees().stream().filter(row -> "emp-li".equals(row.userId())).findFirst().orElseThrow();
        // 整改中：闭环进行中 + 历史进行中；确认/验收各 1；逾期 3（确认、验收、历史）；升级仅闭环 1；已完成 1；累计负责 5
        assertEquals(2, li.executing());
        assertEquals(1, li.confirming());
        assertEquals(1, li.accepting());
        assertEquals(3, li.overdue());
        assertEquals(1, li.escalated());
        assertEquals(1, li.completed());
        assertEquals(5, li.totalAssigned());

        var chen = board.employees().stream().filter(row -> "emp-chen".equals(row.userId())).findFirst().orElseThrow();
        assertEquals(1, chen.executing());
        assertEquals(0, chen.overdue());

        // 无任务员工也占位一行（王工）
        var wang = board.employees().stream().filter(row -> "emp-wang".equals(row.userId())).findFirst().orElseThrow();
        assertEquals(0, wang.totalAssigned());

        assertEquals(3, board.totals().executing());
        assertEquals(1, board.totals().confirming());
        assertEquals(1, board.totals().accepting());
        assertEquals(3, board.totals().overdue());
        assertEquals(1, board.totals().escalated());
        assertEquals(6, board.totals().totalAssigned());
    }

    @Test
    void reviewSummarizesScanAndIssueActivityWithinSelectedMonth() {
        var now = LocalDate.now().atTime(12, 0).atZone(zone).toInstant();
        var prevNoon = YearMonth.now().minusMonths(1).atDay(15).atTime(12, 0).atZone(zone).toInstant();

        var current = scanStore.start(new GovernanceScanRun(
                0, GovernanceScanTriggerType.MANUAL, GovernanceScanRunStatus.RUNNING,
                now, null, 0, 0, 0, 0, "", null, 0));
        scanStore.succeed(current.id(), current.version(), new Counts(10, 2, 0, 0), now);
        var previous = scanStore.start(new GovernanceScanRun(
                0, GovernanceScanTriggerType.MANUAL, GovernanceScanRunStatus.RUNNING,
                prevNoon, null, 0, 0, 0, 0, "", null, 0));
        scanStore.succeed(previous.id(), previous.version(), new Counts(5, 1, 0, 0), prevNoon);

        issueStore.insertAll(List.of(
                issue(1, now, GovernanceIssueStatus.OPEN, GovernanceField.DESCRIPTION),
                issue(2, prevNoon, GovernanceIssueStatus.RESOLVED, GovernanceField.SPECIALTIES)));

        var review = service.board(null).monthly();

        assertEquals(1, review.scanRuns());
        assertEquals(10, review.scannedAssets());
        assertEquals(1, review.prevScanRuns());
        assertEquals(5, review.prevScannedAssets());
        assertEquals(1, review.newIssues());
        assertEquals(1, review.prevNewIssues());
        assertEquals(1, review.openIssues());
    }

    @Test
    void reviewCountsAcceptancePassedClosedLoopTasksPerMonth() {
        var now = LocalDate.now().atTime(12, 0).atZone(zone).toInstant();
        var prevNoon = YearMonth.now().minusMonths(1).atDay(15).atTime(12, 0).atZone(zone).toInstant();
        var today = LocalDate.now();

        taskStore.insert(closedLoopTask(11, "emp-li", "李工", today.minusDays(1), GovernanceTaskStatus.COMPLETED));
        taskStore.insert(closedLoopTask(12, "emp-chen", "陈工", today.minusDays(2), GovernanceTaskStatus.COMPLETED));
        taskStore.insert(closedLoopTask(13, "emp-wang", "王工", today.plusDays(2), GovernanceTaskStatus.COMPLETED));
        // 开放逾期：14 逾期 4 天（达到升级档），15 逾期 1 天（未达档）
        taskStore.insert(closedLoopTask(14, "emp-li", "李工", today.minusDays(4), GovernanceTaskStatus.IN_PROGRESS));
        taskStore.insert(closedLoopTask(15, "emp-chen", "陈工", today.minusDays(1), GovernanceTaskStatus.PENDING_CONFIRMATION));

        // 11 本月验收通过、12 本月与上月各过一轮、13 仅上月通过
        insertPassedAcceptance(11, 1, now);
        insertPassedAcceptance(12, 1, now);
        insertPassedAcceptance(12, 2, prevNoon);
        insertPassedAcceptance(13, 1, prevNoon);

        var review = service.board(null).monthly();

        assertEquals(2, review.closedTasks());
        assertEquals(2, review.prevClosedTasks());
        // 逾期存量只统计未完成任务：14、15；升级仅 14（逾期满 3 天）
        assertEquals(2, review.overdueTasks());
        assertEquals(1, review.escalatedTasks());
    }

    @Test
    void rejectsMalformedMonth() {
        assertThrows(GovernanceValidationException.class, () -> service.board("2026-13"));
        assertThrows(GovernanceValidationException.class, () -> service.board("202609"));
    }

    private GovernanceIssue issue(long id, Instant createdAt, GovernanceIssueStatus status, GovernanceField field) {
        return new GovernanceIssue(id, 100 + id, field, "MISSING_DESCRIPTION", "/description",
                "FIELD-COMPLETENESS", 1, "\"\"", 1, "scope-r4", "HIGH", false, status, null, 0, createdAt, createdAt);
    }

    private void insertPassedAcceptance(long taskId, int governanceRound, Instant completedAt) {
        var thresholds = new java.util.HashMap<GovernanceQualityMetric, Double>();
        var metricResults = new ArrayList<GovernanceAcceptanceMetricResult>();
        for (var metric : GovernanceQualityMetric.values()) {
            thresholds.put(metric, 0.9);
            metricResults.add(new GovernanceAcceptanceMetricResult(
                    0, 0, metric, 0, 0, null, 0.9,
                    GovernanceAcceptanceMetricResult.MetricApplicability.NOT_APPLICABLE, false, List.of(), 0));
        }
        var policy = new GovernanceQualityPolicySnapshot(1, "QUALITY-R4", 1, thresholds, true, false, 0);
        acceptanceStore.createRound(new GovernanceAcceptanceRound(
                0, taskId, governanceRound, policy, metricResults, List.of(),
                GovernanceAcceptanceRound.Status.PASSED, completedAt.minusSeconds(60), completedAt, 0));
    }
}
