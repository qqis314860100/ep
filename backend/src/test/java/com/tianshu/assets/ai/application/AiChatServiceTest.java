package com.tianshu.assets.ai.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tianshu.assets.ai.application.AiCapabilityClient.ChatCitations;
import com.tianshu.assets.ai.application.AiCapabilityClient.ChatDelta;
import com.tianshu.assets.ai.application.AiCapabilityClient.ChatDone;
import com.tianshu.assets.ai.application.AiCapabilityClient.ChatError;
import com.tianshu.assets.ai.application.AiCapabilityClient.ChatEvent;
import com.tianshu.assets.ai.application.AiCapabilityClient.ChatMessage;
import com.tianshu.assets.ai.application.AiCapabilityClient.ChatMeta;
import com.tianshu.assets.ai.application.AiCapabilityClient.ChatRequest;
import com.tianshu.assets.ai.domain.AiChatMessageRole;
import com.tianshu.assets.ai.infrastructure.FakeAiCapabilityClient;
import com.tianshu.assets.ai.infrastructure.InMemoryAiChatRepository;
import com.tianshu.assets.system.infrastructure.InMemorySystemUserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AiChatServiceTest {

    private static final String ADMIN = "emp-admin";
    private static final String SCOPED = "emp-chen";

    private InMemoryAiChatRepository store;
    private FakeAiCapabilityClient capability;
    private AiChatService service;

    @BeforeEach
    void setUp() {
        store = new InMemoryAiChatRepository();
        capability = new FakeAiCapabilityClient();
        var users = new InMemorySystemUserRepository();
        var clock = Clock.fixed(Instant.parse("2026-09-09T09:00:00Z"), ZoneOffset.UTC);
        service = new AiChatService(store, users, capability, "ep-docs", clock);
    }

    private List<ChatEvent> collectChat(Optional<Long> sessionId, String userId, String question) {
        var events = new ArrayList<ChatEvent>();
        service.chat(sessionId, userId, question, events::add);
        return events;
    }

    @Test
    void chatCreatesSessionEmitsEventsAndPersistsTurn() {
        var events = collectChat(Optional.empty(), ADMIN, "这是什么资产？");

        assertThat(events).extracting(event -> event.getClass().getSimpleName())
                .containsExactly("ChatMeta", "ChatDelta", "ChatCitations", "ChatDone");
        assertThat(((ChatMeta) events.getFirst()).sessionId()).isNotBlank();

        var sessions = store.listSessions(ADMIN);
        assertThat(sessions).hasSize(1);
        assertThat(sessions.getFirst().title()).isEqualTo("这是什么资产？");
        var messages = store.listMessages(sessions.getFirst().id());
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).role()).isEqualTo(AiChatMessageRole.USER);
        assertThat(messages.get(0).content()).isEqualTo("这是什么资产？");
        assertThat(messages.get(1).role()).isEqualTo(AiChatMessageRole.ASSISTANT);
        assertThat(messages.get(1).content()).contains("Fake 能力服务");
        assertThat(messages.get(1).citations()).singleElement()
                .satisfies(citation -> assertThat(citation.docId()).isEqualTo("doc-1"));
    }

    @Test
    void chatContinuesExistingSessionAndSendsTrimmedHistory() {
        var firstEvents = collectChat(Optional.empty(), ADMIN, "第一个问题");
        var sessionId = Long.parseLong(((ChatMeta) firstEvents.getFirst()).sessionId());
        collectChat(Optional.of(sessionId), ADMIN, "追问问题");

        var lastRequest = capability.lastRequests().stream()
                .filter(ChatRequest.class::isInstance)
                .map(ChatRequest.class::cast)
                .reduce((first, second) -> second)
                .orElseThrow();
        assertThat(lastRequest.history()).hasSize(2);
        assertThat(lastRequest.history()).extracting(ChatMessage::content)
                .containsExactly("第一个问题", "来自 Fake 能力服务的回答。");
        assertThat(lastRequest.question()).isEqualTo("追问问题");
        assertThat(store.listSessions(ADMIN)).hasSize(1);
    }

    @Test
    void chatSendsScopeFilterNotUserIdentity() {
        collectChat(Optional.empty(), SCOPED, "宁德基地的问题");
        var request = capability.lastRequests().stream()
                .filter(ChatRequest.class::isInstance)
                .map(ChatRequest.class::cast)
                .findFirst().orElseThrow();
        assertThat(request.scopes()).singleElement().satisfies(scope -> {
            assertThat(scope.base()).isEqualTo("宁德基地");
            assertThat(scope.productLine()).isEqualTo("H03");
        });
        assertThat(request.namespace()).isEqualTo("ep-docs");
    }

    @Test
    void historyTrimmedToRecentTurns() {
        var events = collectChat(Optional.empty(), ADMIN, "首个问题");
        var sessionId = Long.parseLong(((ChatMeta) events.getFirst()).sessionId());
        // 再灌 9 轮历史（每轮 user+assistant 两条）使总量超过裁剪窗口
        for (int i = 0; i < 9; i++) {
            collectChat(Optional.of(sessionId), ADMIN, "第 " + i + " 轮问题");
        }
        var request = capability.lastRequests().stream()
                .filter(ChatRequest.class::isInstance)
                .map(ChatRequest.class::cast)
                .reduce((first, second) -> second)
                .orElseThrow();
        assertThat(request.history()).hasSizeLessThanOrEqualTo(16);
        assertThat(request.history()).extracting(ChatMessage::role).contains("user", "assistant");
    }

    @Test
    void midStreamChatErrorStopsTurnWithoutAssistantPersistence() {
        capability.setChatEvents(List.of(
                new ChatDelta("片段一"),
                new ChatError("low_confidence", "证据不足，拒答")));
        var events = collectChat(Optional.empty(), ADMIN, "需要拒答的问题");
        assertThat(events).filteredOn(ChatError.class::isInstance).singleElement()
                .satisfies(error -> assertThat(((ChatError) error).code()).isEqualTo("low_confidence"));
        assertThat(events).noneMatch(ChatDone.class::isInstance);

        var sessions = store.listSessions(ADMIN);
        assertThat(store.listMessages(sessions.getFirst().id()))
                .extracting(message -> message.role())
                .containsExactly(AiChatMessageRole.USER);
    }

    @Test
    void chatForUnknownUserEmitsErrorAndPersistsNothing() {
        var events = collectChat(Optional.empty(), "demo-user", "问题");
        assertThat(events).singleElement().isInstanceOf(ChatError.class);
        assertThat(((ChatError) events.getFirst()).code()).isEqualTo("unauthorized");
        assertThat(store.listSessions("demo-user")).isEmpty();
    }

    @Test
    void chatCapabilityFailureEmitsTypedErrorThenRetrySucceeds() {
        capability.failWith(new AiCapabilityException(
                AiCapabilityException.AiCapabilityError.UNAVAILABLE, "能力服务不可用"));
        var events = collectChat(Optional.empty(), ADMIN, "会失败的问题");
        assertThat(events).filteredOn(ChatError.class::isInstance).singleElement()
                .satisfies(error -> {
                    assertThat(((ChatError) error).code()).isEqualTo("unavailable");
                });
        // 会话与用户提问保留，可重试
        var sessions = store.listSessions(ADMIN);
        assertThat(sessions).hasSize(1);
        assertThat(store.listMessages(sessions.getFirst().id()))
                .extracting(message -> message.role())
                .containsExactly(AiChatMessageRole.USER);

        capability.failWith(null);
        var retryEvents = collectChat(Optional.of(sessions.getFirst().id()), ADMIN, "会失败的问题");
        assertThat(retryEvents).extracting(event -> event.getClass().getSimpleName())
                .contains("ChatDone");
    }

    @Test
    void rejectSessionOfAnotherUserIsNotFound() {
        var events = collectChat(Optional.empty(), ADMIN, "管理员的会话");
        var sessionId = Long.parseLong(((ChatMeta) events.getFirst()).sessionId());
        assertThatThrownBy(() -> service.messages(sessionId, SCOPED))
                .isInstanceOf(AiChatSessionNotFoundException.class);
        assertThatThrownBy(() -> service.renameSession(sessionId, SCOPED, "改名"))
                .isInstanceOf(AiChatSessionNotFoundException.class);
        assertThatThrownBy(() -> service.deleteSession(sessionId, SCOPED))
                .isInstanceOf(AiChatSessionNotFoundException.class);
    }

    @Test
    void renameDeleteAndListSessions() {
        var events = collectChat(Optional.empty(), ADMIN, "初始标题内容稍长一些用于截断测试验证行为");
        var sessionId = Long.parseLong(((ChatMeta) events.getFirst()).sessionId());

        var renamed = service.renameSession(sessionId, ADMIN, "整理后标题");
        assertThat(renamed.title()).isEqualTo("整理后标题");
        assertThat(service.listSessions(ADMIN)).hasSize(1);

        assertThatThrownBy(() -> service.renameSession(sessionId, ADMIN, "  "))
                .isInstanceOf(AiChatValidationException.class);

        service.deleteSession(sessionId, ADMIN);
        assertThat(service.listSessions(ADMIN)).isEmpty();
        assertThatThrownBy(() -> service.messages(sessionId, ADMIN))
                .isInstanceOf(AiChatSessionNotFoundException.class);
    }
}
