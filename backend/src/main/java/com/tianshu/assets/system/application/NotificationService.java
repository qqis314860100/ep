package com.tianshu.assets.system.application;

import com.tianshu.assets.asset.domain.AssetRepository;
import com.tianshu.assets.asset.domain.AssetSearchCriteria;
import com.tianshu.assets.asset.domain.AssetStatus;
import com.tianshu.assets.governance.issue.application.GovernanceIssueStore;
import com.tianshu.assets.governance.issue.domain.GovernanceIssueStatus;
import com.tianshu.assets.governance.scan.application.GovernanceScanRunStore;
import com.tianshu.assets.governance.scan.domain.GovernanceScanRunStatus;
import com.tianshu.assets.governance.task.application.GovernanceTaskStore;
import com.tianshu.assets.governance.task.domain.GovernanceTaskStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 通知聚合：治理任务临近/逾期、待办汇总（待整理/开放问题/异常文件）、扫描失败提醒。
 * 每次请求实时计算；已读状态由前端按通知 id 本地维护。
 */
@Service
public class NotificationService {

    private static final long DUE_WINDOW_DAYS = 7;

    private final GovernanceTaskStore tasks;
    private final GovernanceScanRunStore scans;
    private final GovernanceIssueStore issues;
    private final AssetRepository assets;

    public NotificationService(GovernanceTaskStore tasks, GovernanceScanRunStore scans,
            GovernanceIssueStore issues, AssetRepository assets) {
        this.tasks = tasks;
        this.scans = scans;
        this.issues = issues;
        this.assets = assets;
    }

    public NotificationView notifications() {
        var now = LocalDate.now();
        var items = new ArrayList<NotificationItem>();

        var pendingAssets = assets.search(new AssetSearchCriteria("", null, AssetStatus.PENDING_CURATION,
                "", "", "", "", "", null, 1, 1)).total();
        var openIssues = issues.find(null, GovernanceIssueStatus.OPEN, null);
        var anomalyCount = openIssues.stream().filter(issue -> "ANOMALOUS_FILE".equals(issue.issueType())).count();
        if (pendingAssets > 0 || !openIssues.isEmpty()) {
            items.add(new NotificationItem("todo-summary", "TODO_SUMMARY", "资料待办汇总",
                    String.format("待整理 %d 份 · 开放问题 %d 项 · 异常文件 %d 项", pendingAssets, openIssues.size(), anomalyCount),
                    "/sys/drawing/operations", Instant.now()));
        }

        for (var task : tasks.findAll()) {
            if (task.dueDate() == null) continue;
            if (task.status() == GovernanceTaskStatus.COMPLETED) continue;
            var days = ChronoUnit.DAYS.between(now, task.dueDate());
            if (days > DUE_WINDOW_DAYS) continue;
            var description = days < 0
                    ? "已逾期 " + (-days) + " 天"
                    : days == 0 ? "今天到期" : days + " 天后到期";
            items.add(new NotificationItem("task-" + task.id(), "TASK_DUE",
                    "任务到期 · " + task.name(), description,
                    "/sys/drawing/tasks/" + task.id(), Instant.now()));
        }

        scans.findAll().stream().findFirst().ifPresent(latest -> {
            if (latest.status() == GovernanceScanRunStatus.FAILED) {
                items.add(new NotificationItem("scan-failed", "SCAN_FAILED", "自动扫描失败",
                        latest.errorMessage() == null || latest.errorMessage().isBlank() ? "最近一次扫描运行失败，请重试" : latest.errorMessage(),
                        "/sys/drawing/scans", Instant.now()));
            }
        });

        items.sort(Comparator.comparing(NotificationItem::createdAt).reversed());
        return new NotificationView(items);
    }

    public record NotificationItem(String id, String type, String title, String description, String link, Instant createdAt) {}

    public record NotificationView(List<NotificationItem> items) {}
}
