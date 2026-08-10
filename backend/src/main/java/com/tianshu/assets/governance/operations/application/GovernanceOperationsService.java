package com.tianshu.assets.governance.operations.application;

import com.tianshu.assets.asset.domain.Asset;
import com.tianshu.assets.asset.domain.AssetRepository;
import com.tianshu.assets.asset.domain.AssetSearchCriteria;
import com.tianshu.assets.asset.domain.AssetType;
import com.tianshu.assets.governance.acceptance.application.GovernanceAcceptanceStore;
import com.tianshu.assets.governance.acceptance.domain.GovernanceAcceptanceRound;
import com.tianshu.assets.governance.acceptance.domain.GovernanceOperationJob;
import com.tianshu.assets.governance.confirmation.application.GovernanceConfirmationStore;
import com.tianshu.assets.governance.confirmation.domain.GovernanceConfirmationDecision;
import com.tianshu.assets.governance.confirmation.domain.GovernanceConfirmationRound;
import com.tianshu.assets.governance.issue.application.GovernanceIssueStore;
import com.tianshu.assets.governance.issue.domain.GovernanceIssue;
import com.tianshu.assets.governance.issue.domain.GovernanceIssueStatus;
import com.tianshu.assets.governance.scan.application.GovernanceScanRunStore;
import com.tianshu.assets.governance.scan.domain.GovernanceScanRun;
import com.tianshu.assets.governance.scan.domain.GovernanceScanRunStatus;
import com.tianshu.assets.governance.standard.application.GovernanceDataStandardStore;
import com.tianshu.assets.governance.standard.domain.GovernanceStandardStatus;
import com.tianshu.assets.governance.task.application.GovernanceEmployeeDirectory;
import com.tianshu.assets.governance.task.application.GovernanceTaskStore;
import com.tianshu.assets.governance.task.domain.GovernanceTask;
import com.tianshu.assets.governance.task.domain.GovernanceTaskStatus;
import com.tianshu.assets.governance.task.domain.GovernanceWorkflowVersion;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class GovernanceOperationsService {
    private final AssetRepository assetRepository;
    private final GovernanceIssueStore issueStore;
    private final GovernanceTaskStore taskStore;
    private final GovernanceConfirmationStore confirmationStore;
    private final GovernanceAcceptanceStore acceptanceStore;
    private final GovernanceScanRunStore scanRunStore;
    private final GovernanceEmployeeDirectory employeeDirectory;
    private final GovernanceDataStandardStore standardStore;
    private final Clock clock;

    @Autowired
    public GovernanceOperationsService(
            AssetRepository assetRepository,
            GovernanceIssueStore issueStore,
            GovernanceTaskStore taskStore,
            GovernanceConfirmationStore confirmationStore,
            GovernanceAcceptanceStore acceptanceStore,
            GovernanceScanRunStore scanRunStore,
            GovernanceEmployeeDirectory employeeDirectory,
            GovernanceDataStandardStore standardStore) {
        this(assetRepository, issueStore, taskStore, confirmationStore, acceptanceStore, scanRunStore,
                employeeDirectory, standardStore, Clock.systemUTC());
    }

    GovernanceOperationsService(
            AssetRepository assetRepository,
            GovernanceIssueStore issueStore,
            GovernanceTaskStore taskStore,
            GovernanceConfirmationStore confirmationStore,
            GovernanceAcceptanceStore acceptanceStore,
            GovernanceScanRunStore scanRunStore,
            GovernanceEmployeeDirectory employeeDirectory,
            GovernanceDataStandardStore standardStore,
            Clock clock) {
        this.assetRepository = assetRepository;
        this.issueStore = issueStore;
        this.taskStore = taskStore;
        this.confirmationStore = confirmationStore;
        this.acceptanceStore = acceptanceStore;
        this.scanRunStore = scanRunStore;
        this.employeeDirectory = employeeDirectory;
        this.standardStore = standardStore;
        this.clock = clock;
    }

    public Overview overview(Filter requested) {
        var filter = requested == null ? Filter.empty() : requested.normalized();
        if (filter.fromDate() != null && filter.toDate() != null && filter.fromDate().isAfter(filter.toDate())) {
            throw new IllegalArgumentException("开始日期不能晚于结束日期");
        }
        var now = Instant.now(clock);
        var today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        var employeeNames = employeeDirectory.findAllEmployees().stream()
                .collect(Collectors.toMap(employee -> employee.id(), employee -> employee.name(), (left, right) -> left));
        var requestedOwnerName = filter.ownerUserId().isBlank() ? "" : employeeNames.getOrDefault(filter.ownerUserId(), "");

        var assets = loadAssets().stream()
                .filter(asset -> filter.assetType() == null || asset.assetType() == filter.assetType())
                .filter(asset -> filter.base().isBlank() || asset.scopes().stream()
                        .anyMatch(scope -> scope.base().equalsIgnoreCase(filter.base())))
                .filter(asset -> filter.ownerUserId().isBlank() || asset.ownerName().equals(requestedOwnerName))
                .toList();
        var assetIds = assets.stream().map(Asset::id).collect(Collectors.toSet());
        var issues = issueStore.find(null, null, null).stream()
                .filter(issue -> assetIds.contains(issue.assetId()))
                .filter(issue -> filter.issueType().isBlank()
                        || issue.issueType().equalsIgnoreCase(filter.issueType()))
                .filter(issue -> filter.standardCode().isBlank()
                        || issue.ruleCode().equalsIgnoreCase(filter.standardCode()))
                .toList();
        var relevantTaskIds = issues.stream().map(GovernanceIssue::taskId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        var issueScoped = filter.assetType() != null || !filter.base().isBlank()
                || !filter.issueType().isBlank() || !filter.standardCode().isBlank();
        var tasks = taskStore.findAll().stream()
                .filter(task -> filter.ownerUserId().isBlank() || filter.ownerUserId().equals(task.ownerUserId()))
                .filter(task -> matchesDate(task.dueDate(), filter.fromDate(), filter.toDate()))
                .filter(task -> !issueScoped || relevantTaskIds.contains(task.id()))
                .toList();

        var coveredAssets = assets.stream().filter(asset -> !asset.ownerName().isBlank()
                && employeeNames.containsValue(asset.ownerName())).count();
        var openIssues = issues.stream().filter(issue -> issue.status() == GovernanceIssueStatus.OPEN).count();
        var overdueTasks = tasks.stream().filter(task -> task.status() != GovernanceTaskStatus.COMPLETED
                && task.dueDate() != null && task.dueDate().isBefore(today))
                .sorted(Comparator.comparing(GovernanceTask::dueDate)).toList();

        var metrics = List.of(
                rate("responsibilityCoverage", "责任覆盖率", coveredAssets, assets.size(), "%",
                        "筛选资产与当前员工目录"),
                unavailable("issueClosureCycle", "平均问题关闭周期", "天",
                        "当前问题事实未记录创建和解决时间，暂不生成推测值"),
                recurrenceMetric(),
                rate("automatedTreatmentRate", "自动处理率", 0, issues.size(), "%",
                        "当前自动化等级为 L1，只自动发现问题，不自动覆盖正式资产"),
                firstConfirmationPassRate(tasks, filter),
                acceptancePassRate(tasks, filter),
                reworkRate(tasks),
                applicationSuccessRate(tasks));

        var byType = new LinkedHashMap<String, Long>();
        issues.forEach(issue -> byType.merge(issue.issueType(), 1L, Long::sum));
        var issueBreakdown = byType.entrySet().stream()
                .map(entry -> new Breakdown(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingLong(Breakdown::count).reversed()).toList();
        var risks = overdueTasks.stream().map(task -> new TaskRisk(
                task.id(), task.name(), task.ownerName(), task.dueDate(), task.status())).toList();

        return new Overview(filter, assets.size(), coveredAssets, openIssues, overdueTasks.size(),
                metrics, issueBreakdown, risks, cadences(now, today, openIssues), now);
    }

    private Metric recurrenceMetric() {
        var completed = scanRunStore.findAll().stream()
                .filter(run -> run.status() == GovernanceScanRunStatus.SUCCEEDED).toList();
        long reopened = completed.stream().mapToLong(GovernanceScanRun::reopenedIssueCount).sum();
        long findings = completed.stream()
                .mapToLong(run -> run.createdIssueCount() + run.reopenedIssueCount()).sum();
        return rate("recurrenceRate", "问题复发率", reopened, findings, "%",
                "扫描运行全局汇总，不受资产维度筛选影响");
    }

    private Metric firstConfirmationPassRate(List<GovernanceTask> tasks, Filter filter) {
        long denominator = 0;
        long numerator = 0;
        for (var task : tasks) {
            var first = confirmationStore.rounds(task.id()).stream()
                    .filter(round -> round.status() == GovernanceConfirmationRound.Status.COMPLETED)
                    .filter(round -> matchesInstant(round.completedAt(), filter))
                    .min(Comparator.comparingInt(GovernanceConfirmationRound::governanceRound));
            if (first.isEmpty()) continue;
            denominator++;
            var decisions = confirmationStore.decisions(first.get().id());
            if (decisions.size() == first.get().resultVersionIds().size()
                    && decisions.stream().allMatch(decision ->
                    decision.decision() == GovernanceConfirmationDecision.Decision.APPROVED)) {
                numerator++;
            }
        }
        return rate("firstConfirmationPassRate", "一次确认通过率", numerator, denominator, "%",
                "筛选任务的首个已完成业务确认轮次");
    }

    private Metric acceptancePassRate(List<GovernanceTask> tasks, Filter filter) {
        long denominator = 0;
        long numerator = 0;
        for (var task : tasks) {
            for (var round : acceptanceStore.rounds(task.id())) {
                if (round.status() == GovernanceAcceptanceRound.Status.OPEN
                        || !matchesInstant(round.completedAt(), filter)) continue;
                denominator++;
                if (round.status() == GovernanceAcceptanceRound.Status.PASSED) numerator++;
            }
        }
        return rate("acceptancePassRate", "验收通过率", numerator, denominator, "%",
                "筛选任务的已完成质量验收轮次");
    }

    private Metric reworkRate(List<GovernanceTask> tasks) {
        var started = tasks.stream().filter(task -> task.workflowVersion() == GovernanceWorkflowVersion.CLOSED_LOOP_V1
                && task.status() != GovernanceTaskStatus.DRAFT).toList();
        long reworked = started.stream().filter(task -> task.currentRound() > 1
                || task.status() == GovernanceTaskStatus.REWORK_REQUIRED).count();
        return rate("reworkRate", "返工率", reworked, started.size(), "%", "筛选闭环任务的治理轮次");
    }

    private Metric applicationSuccessRate(List<GovernanceTask> tasks) {
        var jobs = tasks.stream().flatMap(task -> acceptanceStore.applicationJobs(task.id()).stream())
                .filter(job -> job.status() == GovernanceOperationJob.Status.SUCCEEDED
                        || job.status() == GovernanceOperationJob.Status.FAILED).toList();
        long succeeded = jobs.stream().filter(job -> job.status() == GovernanceOperationJob.Status.SUCCEEDED).count();
        return rate("applicationSuccessRate", "正式应用成功率", succeeded, jobs.size(), "%",
                "筛选任务的已结束正式应用作业");
    }

    private List<Cadence> cadences(Instant now, LocalDate today, long openIssues) {
        var latestScan = scanRunStore.findAll().stream().filter(run -> run.status() == GovernanceScanRunStatus.SUCCEEDED)
                .max(Comparator.comparing(GovernanceScanRun::startedAt));
        var scanDue = latestScan.map(run -> run.startedAt().plusSeconds(86_400)).orElse(now);
        var monthEnd = today.with(TemporalAdjusters.lastDayOfMonth());
        var quarterEndMonth = ((today.getMonthValue() - 1) / 3 + 1) * 3;
        var quarterEnd = LocalDate.of(today.getYear(), quarterEndMonth, 1).with(TemporalAdjusters.lastDayOfMonth());
        var enabledStandards = standardStore.findAll().stream()
                .filter(standard -> standard.status() == GovernanceStandardStatus.ENABLED).count();
        return List.of(
                new Cadence("DAILY_SCAN", "每日问题扫描", "内容管理员", scanDue.isAfter(now) ? "ON_TRACK" : "DUE",
                        scanDue.toString(), latestScan.map(run -> "最近成功运行 #" + run.id()).orElse("尚无成功运行")),
                new Cadence("WEEKLY_ASSIGNMENT", "每周问题分派", "内容管理员", openIssues > 0 ? "DUE" : "ON_TRACK",
                        today.plusDays((8 - today.getDayOfWeek().getValue()) % 7).toString(), openIssues + " 个开放问题待处理"),
                new Cadence("MONTHLY_REVIEW", "每月质量复盘", "验收责任人", "PLANNED",
                        monthEnd.toString(), "复盘确认、验收、返工和应用指标"),
                new Cadence("QUARTERLY_STANDARD_REVIEW", "季度标准评审", "标准负责人", "PLANNED",
                        quarterEnd.toString(), enabledStandards + " 项启用标准待纳入评审"));
    }

    private List<Asset> loadAssets() {
        var all = new ArrayList<Asset>();
        var page = 1;
        while (true) {
            var result = assetRepository.search(new AssetSearchCriteria(
                    "", null, null, "", "", "", "", "", null, page, 1000));
            all.addAll(result.items());
            if (all.size() >= result.total() || result.items().isEmpty()) return all;
            page++;
        }
    }

    private boolean matchesInstant(Instant value, Filter filter) {
        return value != null && matchesDate(LocalDate.ofInstant(value, ZoneOffset.UTC), filter.fromDate(), filter.toDate());
    }

    private boolean matchesDate(LocalDate value, LocalDate from, LocalDate to) {
        if (from == null && to == null) return true;
        return value != null && (from == null || !value.isBefore(from)) && (to == null || !value.isAfter(to));
    }

    private Metric rate(String key, String label, long numerator, long denominator, String unit, String source) {
        return new Metric(key, label, denominator == 0 ? null : (double) numerator / denominator,
                numerator, denominator, denominator > 0, unit, source);
    }

    private Metric unavailable(String key, String label, String unit, String source) {
        return new Metric(key, label, null, 0, 0, false, unit, source);
    }

    public record Filter(
            String standardCode,
            String issueType,
            String ownerUserId,
            AssetType assetType,
            String base,
            LocalDate fromDate,
            LocalDate toDate) {
        public Filter {
            standardCode = standardCode == null ? "" : standardCode;
            issueType = issueType == null ? "" : issueType;
            ownerUserId = ownerUserId == null ? "" : ownerUserId;
            base = base == null ? "" : base;
        }

        public static Filter empty() {
            return new Filter("", "", "", null, "", null, null);
        }

        Filter normalized() {
            return new Filter(standardCode.trim(), issueType.trim(), ownerUserId.trim(), assetType,
                    base.trim(), fromDate, toDate);
        }
    }

    public record Overview(
            Filter filter,
            long assetCount,
            long coveredAssetCount,
            long openIssueCount,
            long overdueTaskCount,
            List<Metric> metrics,
            List<Breakdown> issuesByType,
            List<TaskRisk> overdueTasks,
            List<Cadence> cadences,
            Instant generatedAt) {}

    public record Metric(
            String key,
            String label,
            Double value,
            long numerator,
            long denominator,
            boolean available,
            String unit,
            String source) {}

    public record Breakdown(String key, long count) {}

    public record TaskRisk(
            long taskId,
            String taskName,
            String ownerName,
            LocalDate dueDate,
            GovernanceTaskStatus status) {}

    public record Cadence(
            String key,
            String name,
            String ownerRole,
            String status,
            String nextDueAt,
            String evidence) {}
}
