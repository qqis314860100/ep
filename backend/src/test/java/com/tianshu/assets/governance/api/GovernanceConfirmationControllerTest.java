package com.tianshu.assets.governance.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import com.tianshu.assets.common.api.ApiExceptionHandler;
import com.tianshu.assets.governance.support.GovernanceTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

class GovernanceConfirmationControllerTest {

    private MockMvc mockMvc;
    private long taskId;
    private long roundId;
    private long roundVersion;
    private long firstItemId;
    private long secondItemId;
    private String firstOwner;
    private String secondOwner;

    @BeforeEach
    void setUp() {
        var fixture = GovernanceTestFixture.fieldClosure();
        var round = fixture.pendingConfirmationWithTwoItems();
        var current = fixture.confirmationService().current(round.taskId());
        mockMvc = standaloneSetup(new GovernanceConfirmationController(fixture.confirmationService()))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
        taskId = round.taskId();
        roundId = round.id();
        roundVersion = round.version();
        firstItemId = current.items().get(0).itemId();
        secondItemId = current.items().get(1).itemId();
        firstOwner = current.items().get(0).responsibleUserId();
        secondOwner = current.items().get(1).responsibleUserId();
    }

    @Test
    void queriesDecidesAndCompletesCurrentRoundWithoutReturningFieldValues() throws Exception {
        mockMvc.perform(get("/api/v1/governance/tasks/{taskId}/confirmation-rounds/current", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.round.id").value(roundId))
                .andExpect(jsonPath("$.coveredCount").value(0))
                .andExpect(jsonPath("$.coverageRate").value(0.0))
                .andExpect(jsonPath("$.approvalRate").value(0.0))
                .andExpect(jsonPath("$.items[0].originalValueJson").doesNotExist())
                .andExpect(jsonPath("$.items[0].proposedValueJson").doesNotExist());

        decide(firstItemId, firstOwner, "APPROVED", "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coveredCount").value(1))
                .andExpect(jsonPath("$.decisions[0].version").value(0));
        decide(secondItemId, secondOwner, "APPROVED", "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coveredCount").value(2));

        mockMvc.perform(post("/api/v1/governance/tasks/{taskId}/confirmation-rounds/{roundId}/complete",
                        taskId, roundId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roundVersion\":" + roundVersion + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskStatus").value("PENDING_ACCEPTANCE"))
                .andExpect(jsonPath("$.coverageRate").value(1.0))
                .andExpect(jsonPath("$.approvalRate").value(1.0));
    }

    @Test
    void validatesRejectedCommentAtApiBoundary() throws Exception {
        decide(firstItemId, firstOwner, "REJECTED", " ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("validation_error"));
    }

    private org.springframework.test.web.servlet.ResultActions decide(
            long itemId, String owner, String decision, String comment) throws Exception {
        return mockMvc.perform(put(
                        "/api/v1/governance/confirmation-rounds/{roundId}/items/{itemId}/decision",
                        roundId, itemId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"" + decision + "\",\"comment\":\"" + comment
                        + "\",\"decisionVersion\":0,\"confirmerUserId\":\"" + owner + "\"}"));
    }
}
