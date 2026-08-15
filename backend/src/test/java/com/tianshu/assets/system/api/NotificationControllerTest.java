package com.tianshu.assets.system.api;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import com.tianshu.assets.asset.domain.AssetPage;
import com.tianshu.assets.asset.domain.AssetRepository;
import com.tianshu.assets.asset.infrastructure.InMemoryAssetRepository;
import com.tianshu.assets.common.api.ApiExceptionHandler;
import com.tianshu.assets.governance.infrastructure.InMemoryGovernanceIssueStore;
import com.tianshu.assets.governance.infrastructure.InMemoryGovernanceScanRunStore;
import com.tianshu.assets.governance.infrastructure.InMemoryGovernanceTaskStore;
import com.tianshu.assets.governance.scan.application.GovernanceScanRunStore.Counts;
import com.tianshu.assets.governance.scan.domain.GovernanceScanRun;
import com.tianshu.assets.governance.scan.domain.GovernanceScanRunStatus;
import com.tianshu.assets.governance.scan.domain.GovernanceScanTriggerType;
import com.tianshu.assets.governance.task.domain.GovernanceTask;
import com.tianshu.assets.governance.task.domain.GovernanceTaskStatus;
import com.tianshu.assets.governance.task.domain.GovernanceWorkflowVersion;
import com.tianshu.assets.system.application.NotificationService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

class NotificationControllerTest {

    private MockMvc mockMvc;
    private InMemoryGovernanceTaskStore taskStore;
    private InMemoryGovernanceScanRunStore scanStore;
    private InMemoryGovernanceIssueStore issueStore;

    @BeforeEach
    void setUp() {
        taskStore = new InMemoryGovernanceTaskStore();
        scanStore = new InMemoryGovernanceScanRunStore();
        issueStore = new InMemoryGovernanceIssueStore();
        var service = new NotificationService(taskStore, scanStore, issueStore, new InMemoryAssetRepository());
        mockMvc = standaloneSetup(new NotificationController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    private GovernanceTask legacyTask(long id, String name, LocalDate dueDate, GovernanceTaskStatus status) {
        return new GovernanceTask(id, "GOV-NOTIFY-" + id, name, "通知测试", "LEGACY_MANUAL_PROGRESS",
                "emp-chen", "陈工", "emp-chen", dueDate, status, 0,
                GovernanceWorkflowVersion.LEGACY_PROGRESS, null, null, 0, 0, 0);
    }

    @Test
    void returnsTodoSummaryWithPendingAssetsAndOpenIssues() throws Exception {
        issueStore.insertAll(issueStore.withFieldSeeds().find(null, null, null));

        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id", hasItem("todo-summary")))
                .andExpect(jsonPath("$.items[?(@.id=='todo-summary')].title",
                        hasItem("资料待办汇总")))
                .andExpect(jsonPath("$.items[?(@.id=='todo-summary')].description",
                        hasItem("待整理 2 份 · 开放问题 5 项 · 异常文件 0 项")));
    }

    @Test
    void omitsTodoSummaryWhenNothingPending() throws Exception {
        var emptyAssets = mock(AssetRepository.class);
        when(emptyAssets.search(any())).thenReturn(new AssetPage(List.of(), 0, 1, 1));
        var service = new NotificationService(taskStore, scanStore, issueStore, emptyAssets);
        mockMvc = standaloneSetup(new NotificationController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();

        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id", hasSize(0)));
    }

    @Test
    void includesDueOverdueAndExcludesCompletedOrDistantTasks() throws Exception {
        var now = LocalDate.now();
        taskStore.insert(legacyTask(1, "临近到期任务", now.plusDays(3), GovernanceTaskStatus.IN_PROGRESS));
        taskStore.insert(legacyTask(2, "已逾期任务", now.minusDays(1), GovernanceTaskStatus.IN_PROGRESS));
        taskStore.insert(legacyTask(3, "已完成任务", now.plusDays(2), GovernanceTaskStatus.COMPLETED));
        taskStore.insert(legacyTask(4, "远期任务", now.plusDays(30), GovernanceTaskStatus.IN_PROGRESS));

        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id=='task-1')].description", hasItem("3 天后到期")))
                .andExpect(jsonPath("$.items[?(@.id=='task-2')].description", hasItem("已逾期 1 天")))
                .andExpect(jsonPath("$.items[*].id").value(org.hamcrest.Matchers.not(hasItem("task-3"))))
                .andExpect(jsonPath("$.items[*].id").value(org.hamcrest.Matchers.not(hasItem("task-4"))));
    }

    @Test
    void includesLatestFailedScanNotification() throws Exception {
        var started = scanStore.start(new GovernanceScanRun(
                0, GovernanceScanTriggerType.SCHEDULED, GovernanceScanRunStatus.RUNNING,
                Instant.now(), null, 0, 0, 0, 0, "", null, 0));
        scanStore.fail(started.id(), started.version(), new Counts(10, 2, 0, 0),
                "数据库连接失败", Instant.now());

        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id=='scan-failed')].title", hasItem("自动扫描失败")))
                .andExpect(jsonPath("$.items[?(@.id=='scan-failed')].description", hasItem("数据库连接失败")));
    }

    @Test
    void ignoresSucceededScan() throws Exception {
        var started = scanStore.start(new GovernanceScanRun(
                0, GovernanceScanTriggerType.SCHEDULED, GovernanceScanRunStatus.RUNNING,
                Instant.now(), null, 0, 0, 0, 0, "", null, 0));
        scanStore.succeed(started.id(), started.version(), new Counts(10, 2, 0, 0), Instant.now());

        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id").value(org.hamcrest.Matchers.not(hasItem("scan-failed"))));
    }
}
