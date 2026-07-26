package com.tianshu.assets.governance.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import com.tianshu.assets.common.api.ApiExceptionHandler;
import com.tianshu.assets.governance.support.GovernanceTestFixture;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class GovernanceAcceptanceControllerTest {

    @Test
    void queriesChecksCompletesAndOpensRework() throws Exception {
        var fixture = GovernanceTestFixture.fieldClosure();
        var round = fixture.pendingAcceptance();
        var sample = round.samples().getFirst();
        var affectedItemId = fixture.executionStore().items(round.taskId()).get(1).id();
        round = fixture.failMetric(
                round.id(),
                com.tianshu.assets.governance.acceptance.domain.GovernanceQualityMetric.OWNER_COVERAGE,
                java.util.List.of(affectedItemId));
        var mockMvc = standaloneSetup(new GovernanceAcceptanceController(
                        fixture.acceptanceService(), fixture.qualityService(), fixture.reworkService()))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();

        mockMvc.perform(get("/api/v1/governance/tasks/{taskId}/acceptance-rounds/current", round.taskId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(round.id()))
                .andExpect(jsonPath("$.status").value("OPEN"));

        mockMvc.perform(put("/api/v1/governance/acceptance-rounds/{roundId}/samples/{itemId}",
                        round.id(), sample.itemId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"passed\":true,\"issueDescription\":\"\","
                                + "\"reviewerUserId\":\"qa-1\",\"sampleVersion\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passed").value(true));

        var current = fixture.acceptanceService().current(round.taskId());
        mockMvc.perform(post("/api/v1/governance/tasks/{taskId}/acceptance-rounds/{roundId}/complete",
                        round.taskId(), round.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roundVersion\":" + current.version()
                                + ",\"operatorUserId\":\"qa-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskStatus").value("REWORK_REQUIRED"));

        var task = fixture.taskStore().findById(round.taskId()).orElseThrow();
        mockMvc.perform(post("/api/v1/governance/tasks/{taskId}/rework", task.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskVersion\":" + task.version()
                                + ",\"reason\":\"补充责任信息\",\"actorUserId\":\"emp-chen\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.currentRound").value(task.currentRound() + 1));
    }
}
