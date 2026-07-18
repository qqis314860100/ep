package com.tianshu.assets.governance.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import com.tianshu.assets.common.api.ApiExceptionHandler;
import com.tianshu.assets.governance.application.GovernanceTaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

class GovernanceTaskControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = standaloneSetup(new GovernanceTaskController(new GovernanceTaskService()))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void listsSeededGovernanceTasks() throws Exception {
        mockMvc.perform(get("/api/v1/governance/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("IN_PROGRESS"));
    }

    @Test
    void createsGovernanceTask() throws Exception {
        mockMvc.perform(post("/api/v1/governance/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"平台子类映射\",\"scope\":\"八大平台\",\"owner\":\"陈工\",\"assigneeId\":\"emp-chen\",\"total\":20,\"dueDate\":\"2026-09-01\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("平台子类映射"))
                .andExpect(jsonPath("$.assigneeId").value("emp-chen"))
                .andExpect(jsonPath("$.completed").value(0));
    }

    @Test
    void listsEmployeesFromDirectory() throws Exception {
        mockMvc.perform(get("/api/v1/governance/tasks/employees"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].source").value("OFFICE_DIRECTORY"));
    }

    @Test
    void listsAndUpdatesPlans() throws Exception {
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
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"));

        mockMvc.perform(post("/api/v1/governance/tasks/1/plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"补充验收抽样记录\",\"plannedStart\":\"2026-08-13\",\"plannedEnd\":\"2026-08-14\",\"plannedQuantity\":30,\"quantityUnit\":\"个资产\",\"assigneeId\":\"emp-wang\",\"dependencyIds\":[102]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.plannedEnd[0]").value(2026))
                .andExpect(jsonPath("$.plannedEnd[1]").value(8))
                .andExpect(jsonPath("$.plannedEnd[2]").value(14))
                .andExpect(jsonPath("$.plannedQuantity").value(30));
    }

    @Test
    void updatesTaskProgress() throws Exception {
        mockMvc.perform(patch("/api/v1/governance/tasks/1/progress")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"completed\":180}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completed").value(180))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }
}
