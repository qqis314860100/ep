package com.tianshu.assets.ai.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tianshu.assets.ai.application.AiCapabilityClient.ChatEvent;
import com.tianshu.assets.ai.application.AiChatService;
import com.tianshu.assets.ai.infrastructure.FakeAiCapabilityClient;
import com.tianshu.assets.ai.infrastructure.InMemoryAiChatRepository;
import com.tianshu.assets.common.api.ApiExceptionHandler;
import com.tianshu.assets.system.infrastructure.InMemorySystemUserRepository;
import java.util.ArrayList;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

class AiChatControllerTest {

    private static final String ADMIN = "emp-admin";

    private MockMvc mockMvc;
    private InMemoryAiChatRepository store;
    private FakeAiCapabilityClient capability;
    private AiChatService service;

    @BeforeEach
    void setUp() {
        store = new InMemoryAiChatRepository();
        capability = new FakeAiCapabilityClient();
        var users = new InMemorySystemUserRepository();
        service = new AiChatService(store, users, capability, "ep-docs");
        mockMvc = standaloneSetup(new AiChatController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    /** 触发一轮问答（应用层直连，同步），返回会话 id。 */
    private long runTurn(String userId, String question) {
        var events = new ArrayList<ChatEvent>();
        var turn = service.chat(Optional.empty(), userId, question, events::add);
        return turn.sessionId();
    }

    private String drainSse(MvcResult result) throws Exception {
        MvcResult dispatched = null;
        for (int i = 0; i < 60; i++) {
            try {
                dispatched = mockMvc.perform(asyncDispatch(result)).andReturn();
                if (dispatched.getResponse().getContentAsString().contains("event:done")
                        || dispatched.getResponse().getContentAsString().contains("event:error")
                        || i > 40) {
                    break;
                }
            } catch (Exception ignored) {
                // 流尚未完成，稍后再取
            }
            Thread.sleep(50);
        }
        return dispatched == null ? "" : dispatched.getResponse().getContentAsString();
    }

    @Test
    void chatStreamsSseEventsAndPersistsSession() throws Exception {
        var result = mockMvc.perform(post("/api/v1/ai/chat")
                        .header("X-User-Id", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"这是什么资产？\"}"))
                .andExpect(request().asyncStarted())
                .andExpect(status().isOk())
                .andReturn();
        var body = drainSse(result);

        assertThat(body).contains("event:meta");
        assertThat(body).contains("event:delta");
        assertThat(body).contains("event:citations");
        assertThat(body).contains("event:done");
        assertThat(body).doesNotContain("event:error");
        assertThat(body.indexOf("event:meta")).isLessThan(body.indexOf("event:delta"));
        assertThat(body.indexOf("event:delta")).isLessThan(body.indexOf("event:citations"));
        assertThat(body.indexOf("event:citations")).isLessThan(body.indexOf("event:done"));
        assertThat(store.listSessions(ADMIN)).hasSize(1);
    }

    @Test
    void capabilityFailureEmitsTypedErrorEventThroughSse() throws Exception {
        capability.failWith(new com.tianshu.assets.ai.application.AiCapabilityException(
                com.tianshu.assets.ai.application.AiCapabilityException.AiCapabilityError.UNAVAILABLE,
                "能力服务不可用"));
        var result = mockMvc.perform(post("/api/v1/ai/chat")
                        .header("X-User-Id", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"会失败的问题\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();
        var body = drainSse(result);
        assertThat(body).contains("event:error");
        assertThat(body).contains("\"code\":\"unavailable\"");
        // 会话与用户提问保留可重试
        assertThat(store.listSessions(ADMIN)).hasSize(1);
        assertThat(store.listMessages(store.listSessions(ADMIN).getFirst().id())).hasSize(1);
    }

    @Test
    void chatWithBlankQuestionEmitsErrorEvent() throws Exception {
        var result = mockMvc.perform(post("/api/v1/ai/chat")
                        .header("X-User-Id", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"  \"}"))
                .andExpect(request().asyncStarted())
                .andReturn();
        assertThat(drainSse(result)).contains("event:error");
    }

    @Test
    void sessionCrudEndpointsRespectOwnership() throws Exception {
        var sessionId = runTurn(ADMIN, "管理员的会话");

        mockMvc.perform(get("/api/v1/ai/sessions").header("X-User-Id", ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(sessionId));

        mockMvc.perform(get("/api/v1/ai/sessions/" + sessionId + "/messages")
                        .header("X-User-Id", ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].citations[0].docId").value("doc-1"));

        mockMvc.perform(get("/api/v1/ai/sessions/" + sessionId + "/messages")
                        .header("X-User-Id", "emp-chen"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ai_chat_session_not_found"));

        mockMvc.perform(patch("/api/v1/ai/sessions/" + sessionId)
                        .header("X-User-Id", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(java.util.Map.of("name", "整理后的标题"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("整理后的标题"));

        mockMvc.perform(delete("/api/v1/ai/sessions/" + sessionId)
                        .header("X-User-Id", ADMIN))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/ai/sessions").header("X-User-Id", ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void sessionsListIsEmptyForUnknownUser() throws Exception {
        mockMvc.perform(get("/api/v1/ai/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void renameWithBlankTitleIsValidationError() throws Exception {
        var sessionId = runTurn(ADMIN, "管理员的会话");

        mockMvc.perform(patch("/api/v1/ai/sessions/" + sessionId)
                        .header("X-User-Id", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("ai_chat_invalid"));
    }
}
