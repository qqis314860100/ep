package com.tianshu.assets.system.application;

import com.tianshu.assets.asset.domain.AssetRepository;
import com.tianshu.assets.asset.domain.AssetSearchCriteria;
import com.tianshu.assets.asset.domain.AssetStatus;
import com.tianshu.assets.governance.issue.application.GovernanceIssueStore;
import com.tianshu.assets.governance.issue.domain.GovernanceIssueStatus;
import com.tianshu.assets.governance.scan.application.GovernanceScanRunStore;
import com.tianshu.assets.governance.scan.domain.GovernanceScanRunStatus;
import com.tianshu.assets.governance.task.application.GovernanceTaskStore;
import com.tianshu.assets.governance.task.domain.GovernanceTask;
import com.tianshu.assets.governance.task.domain.GovernanceTaskStatus;
import com.tianshu.assets.governance.task.domain.GovernanceWorkflowVersion;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * 通知聚合（R3 逾期升级强化后）：
 * <ul>
 *   <li>到期/逾期提醒（TASK_DUE）按请求身份定向——登录员工只看到自己名下任务；
 *       匿名（无会话）维持 demo 兼容的全局提醒。</li>
 *   <li>闭环治理任务逾期满 3/7/14 天，向内容/系统管理员生成升级提醒
 *       （TASK_ESCALATED）；同一任务按达到的最高档位只生成一条，档位跃迁视为新提醒。</li>
 *   <li>资料待办汇总、扫描失败为组织级提醒，对登录与匿名请求均可见。</li>
 * </ul>
 * 每次请求实时计算；已读状态由前端按通知 id 本地维护（升级 id 含档位，档位跃迁重新未读）。
 */
@Service
public class NotificationService {

    private static final long DUE_WINDOW_DAYS = 7;

    /** 逾期升级档位（天）：逾期满 N 天升级一次（用户拍板：3 天首次，7/14 天持续逾期再提醒）。 */
    private static final int[] ESCALATION_LEVEL_DAYS = {3, 7, 14};

    /** 升级提醒接收角色（治理管理员视角：内容管理员 / 系统管理员）。 */
    private static final Set<String> ESCALATION_RECIPIENT_ROLES = Set.of("CONTENT_ADMIN", "SYSTEM_ADMIN");

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

    /** 无会话/无身份头（demo 兼容）：全局到期提醒，不含升级。 */
    public NotificationView notifications() {
        return notifications(null, Set.of());
    }

    /**
     * 按请求身份计算通知视图。
     *
     * @param actorUserId 会话用户（经 SessionIdentityFilter 覆写）；匿名请求为 null
     * @param actorRoles  会话角色（逗号分隔解析）；匿名请求为空集
     */
    public NotificationView notifications(String actorUserId, Set<String> actorRoles) {
        var now = LocalDate.now();
        var dueItems = new ArrayList<NotificationItem>();
        var escalations = new ArrayList<NotificationItem>();

        var pendingAssets = assets.search(new AssetSearchCriteria("", null, AssetStatus.PENDING_CURATION,
                "", "", "", "", "", null, 1, 1)).total();
        var openIssues = issues.find(null, GovernanceIssueStatus.OPEN, null);
        var anomalyCount = openIssues.stream().filter(issue -> "ANOMALOUS_FILE".equals(issue.issueType())).count();
        if (pendingAssets > 0 || !openIssues.isEmpty()) {
            dueItems.add(new NotificationItem("todo-summary", "TODO_SUMMARY", "资料待办汇总",
                    String.format("待整理 %d 份 · 开放问题 %d 项 · 异常文件 %d 项", pendingAssets, openIssues.size(), anomalyCount),
                    "/sys/drawing/operations", Instant.now()));
        }

        boolean anonymous = actorUserId == null || actorUserId.isBlank();
        boolean escalationAudience = actorRoles != null
                && actorRoles.stream().anyMatch(ESCALATION_RECIPIENT_ROLES::contains);

        for (var task : tasks.findAll()) {
            if (task.dueDate() == null) continue;
            if (task.status() == GovernanceTaskStatus.COMPLETED) continue;
            var days = ChronoUnit.DAYS.between(now, task.dueDate());
            if (days > DUE_WINDOW_DAYS) continue;
            var overdueDays = days < 0 ? (int) -days : 0;
            int escalationLevel = escalationAudience && isClosedLoop(task)
                    ? escalationLevelFor(overdueDays)
                    : 0;
            if (escalationLevel > 0) {
                escalations.add(escalationItem(task, escalationLevel, overdueDays));
            } else {
                boolean own = !anonymous
                        && task.ownerUserId() != null && task.ownerUserId().equals(actorUserId);
                if (anonymous || own) {
                    dueItems.add(dueItem(task, days));
                }
            }
        }

        scans.findAll().stream().findFirst().ifPresent(latest -> {
            if (latest.status() == GovernanceScanRunStatus.FAILED) {
                dueItems.add(new NotificationItem("scan-failed", "SCAN_FAILED", "自动扫描失败",
                        latest.errorMessage() == null || latest.errorMessage().isBlank()
                                ? "最近一次扫描运行失败，请重试" : latest.errorMessage(),
                        "/sys/drawing/scans", Instant.now()));
            }
        });

        dueItems.sort(Comparator.comparing(NotificationItem::createdAt).reversed());
        var items = new ArrayList<NotificationItem>(escalations.size() + dueItems.size());
        // 升级提醒排在最前（管理员的处理对象），随后为到期提醒与组织级提醒。
        items.addAll(escalations);
        items.addAll(dueItems);
        return new NotificationView(items);
    }

    private boolean isClosedLoop(GovernanceTask task) {
        return task.workflowVersion() == GovernanceWorkflowVersion.CLOSED_LOOP_V1;
    }

    /** 返回 overdueDays 命中的最高升级档位（3/7/14），未达到 3 天返回 0。 */
    private int escalationLevelFor(long overdueDays) {
        for (int index = ESCALATION_LEVEL_DAYS.length - 1; index >= 0; index--) {
            if (overdueDays >= ESCALATION_LEVEL_DAYS[index]) {
                return ESCALATION_LEVEL_DAYS[index];
            }
        }
        return 0;
    }

    private NotificationItem dueItem(GovernanceTask task, long days) {
        var description = days < 0
                ? "已逾期 " + (-days) + " 天"
                : days == 0 ? "今天到期" : days + " 天后到期";
        return new NotificationItem("task-" + task.id(), "TASK_DUE",
                "任务到期 · " + task.name(), description,
                "/sys/drawing/tasks/" + task.id(), Instant.now());
    }

    private NotificationItem escalationItem(GovernanceTask task, int level, long overdueDays) {
        var owner = task.ownerName() == null || task.ownerName().isBlank()
                ? (task.ownerUserId() == null ? "未分配" : task.ownerUserId())
                : task.ownerName();
        String title;
        String description;
        if (level >= 14) {
            title = "严重逾期 · " + task.name();
            description = String.format("「%s」负责 · 已逾期 %d 天，请督办处理或改派", owner, overdueDays);
        } else if (level >= 7) {
            title = "逾期再升级 · " + task.name();
            description = String.format("「%s」负责 · 已逾期 %d 天仍未完成，请尽快介入或改派", owner, overdueDays);
        } else {
            title = "逾期升级 · " + task.name();
            description = String.format("「%s」负责 · 已逾期 %d 天未完成，请跟进或改派", owner, overdueDays);
        }
        return new NotificationItem(
                "task-escalated-" + level + "-" + task.id(), "TASK_ESCALATED", title, description,
                "/sys/drawing/tasks/" + task.id(), Instant.now());
    }

    public record NotificationItem(String id, String type, String title, String description, String link, Instant createdAt) {}

    public record NotificationView(List<NotificationItem> items) {}
}
