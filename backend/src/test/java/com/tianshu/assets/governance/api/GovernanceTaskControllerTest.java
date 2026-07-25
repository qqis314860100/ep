package com.tianshu.assets.governance.api;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import com.tianshu.assets.common.api.ApiExceptionHandler;
import com.tianshu.assets.governance.infrastructure.InMemoryGovernanceEmployeeDirectory;
import com.tianshu.assets.governance.infrastructure.InMemoryGovernanceTaskStore;
import com.tianshu.assets.governance.task.application.GovernanceTaskApplicationService;
import com.tianshu.assets.governance.task.domain.GovernanceTask;
import com.tianshu.assets.governance.task.domain.GovernanceTaskStatus;
import com.tianshu.assets.governance.task.domain.GovernanceWorkflowVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

class GovernanceTaskControllerTest {

    private MockMvc mockMvc;
    private GovernanceTaskApplicationService service;

    @BeforeEach
    void setUp() {
        service = new GovernanceTaskApplicationService(
                InMemoryGovernanceTaskStore.withLegacySeed(), new InMemoryGovernanceEmployeeDirectory());
        mockMvc = standaloneSetup(new GovernanceTaskController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void listsSeededGovernanceTasks() throws Exception {
        mockMvc.perform(get("/api/v1/governance/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$[0].workflowVersion").value("LEGACY_PROGRESS"))
                .andExpect(jsonPath("$[0].currentRound").value(0))
                .andExpect(jsonPath("$[0].version").value(0))
                .andExpect(jsonPath("$[0].total").value(286))
                .andExpect(jsonPath("$[0].completed").value(174))
                .andExpect(jsonPath("$[0].editable").value(false));
    }

    @Test
    void serializesMissingLegacyDueDateAsNull() throws Exception {
        var store = new InMemoryGovernanceTaskStore();
        store.insert(new GovernanceTask(
                0, "GOV-LEGACY-NULL-DATE", "未排期历史任务", "历史导入", "LEGACY_IMPORT",
                "emp-wang", "王工", "emp-wang", null, GovernanceTaskStatus.IN_PROGRESS, 0,
                GovernanceWorkflowVersion.LEGACY_PROGRESS, null, null, 12, 3, 0));
        mockMvc = standaloneSetup(new GovernanceTaskController(new GovernanceTaskApplicationService(
                        store, new InMemoryGovernanceEmployeeDirectory())))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();

        mockMvc.perform(get("/api/v1/governance/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].dueDate").value(nullValue()));
    }

    @Test
    void rejectsLegacyTaskCreationWithoutIssues() throws Exception {
        mockMvc.perform(post("/api/v1/governance/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"平台子类映射\",\"scope\":\"八大平台\",\"owner\":\"陈工\",\"assigneeId\":\"emp-chen\",\"total\":20,\"dueDate\":\"2026-09-01\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("governance_task_state_conflict"))
                .andExpect(jsonPath("$.error.message").value("历史进度任务为只读，请按问题池重新建单"));
    }

    @Test
    void listsEmployeesFromDirectory() throws Exception {
        mockMvc.perform(get("/api/v1/governance/tasks/employees"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].source").value("OFFICE_DIRECTORY"));
    }

    @Test
    void listsPlansButRejectsLegacyPlanMutations() throws Exception {
        mockMvc.perform(get("/api/v1/governance/tasks/1/plans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("DONE"))
                .andExpect(jsonPath("$[0].plannedStart[0]").value(2026))
                .andExpect(jsonPath("$[0].plannedStart[1]").value(8))
                .andExpect(jsonPath("$[0].plannedStart[2]").value(1))
                .andExpect(jsonPath("$[0].plannedQuantity").value(286));

        mockMvc.perform(patch("/api/v1/governance/tasks/1/plans/102")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DONE\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("governance_task_state_conflict"));

        mockMvc.perform(post("/api/v1/governance/tasks/1/plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"补充验收抽样记录\",\"plannedStart\":\"2026-08-13\",\"plannedEnd\":\"2026-08-14\",\"plannedQuantity\":30,\"quantityUnit\":\"个资产\",\"assigneeId\":\"emp-wang\",\"dependencyIds\":[102]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("governance_task_state_conflict"));
    }

    @Test
    void rejectsLegacyStatusCommand() throws Exception {
        mockMvc.perform(patch("/api/v1/governance/tasks/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("governance_task_state_conflict"));
    }

    @Test
    void rejectsLegacyTaskProgressMutation() throws Exception {
        mockMvc.perform(patch("/api/v1/governance/tasks/1/progress")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"completed\":180}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("governance_task_state_conflict"));
    }
}
