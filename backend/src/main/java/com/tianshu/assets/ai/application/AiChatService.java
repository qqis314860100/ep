package com.tianshu.assets.ai.application;

import com.tianshu.assets.ai.application.AiCapabilityClient.ChatCitations;
import com.tianshu.assets.ai.application.AiCapabilityClient.ChatDelta;
import com.tianshu.assets.ai.application.AiCapabilityClient.ChatDone;
import com.tianshu.assets.ai.application.AiCapabilityClient.ChatError;
import com.tianshu.assets.ai.application.AiCapabilityClient.ChatEvent;
import com.tianshu.assets.ai.application.AiCapabilityClient.ChatMessage;
import com.tianshu.assets.ai.application.AiCapabilityClient.ChatMeta;
import com.tianshu.assets.ai.application.AiCapabilityClient.ChatRequest;
import com.tianshu.assets.ai.application.AiCapabilityClient.Citation;
import com.tianshu.assets.ai.domain.AiChatMessage;
import com.tianshu.assets.ai.domain.AiChatMessageRole;
import com.tianshu.assets.ai.domain.AiChatSession;
import com.tianshu.assets.ai.domain.AiChatRepository;
import com.tianshu.assets.ai.domain.AiCitation;
import com.tianshu.assets.ai.domain.AiTargetScope;
import com.tianshu.assets.system.domain.SystemUser;
import com.tianshu.assets.system.domain.SystemUserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * AI 问答会话应用服务（AI 一期 T3）。
 *
 * <p>库为真源：会话/消息存 ep（可审计、可随用户权限回收）；能力服务无状态。
 * 每轮：新建/续接会话 → 用户提问即时落库 → 携带裁剪后的历史与用户 AssetScope（不携带身份）
 * 经 {@link AiCapabilityClient} 流式问答 → 流结束后助手消息与引用整体落库。
 * 能力服务超时/不可用 → 以 ChatError 事件结束（会话与已落库的用户提问保留，可重试）。</p>
 */
@Service
public class AiChatService {

    private static final int MAX_HISTORY_TURNS = 8;
    private static final int MAX_TITLE_LENGTH = 30;

    private final AiChatRepository sessions;
    private final SystemUserRepository users;
    private final AiCapabilityClient capability;
    private final String namespace;
    private final Clock clock;

    @Autowired
    public AiChatService(AiChatRepository sessions, SystemUserRepository users, AiCapabilityClient capability,
            @Value("${ai.capability.namespace:ep-docs}") String namespace) {
        this(sessions, users, capability, namespace, Clock.systemUTC());
    }

    public AiChatService(AiChatRepository sessions, SystemUserRepository users, AiCapabilityClient capability,
            String namespace, Clock clock) {
        this.sessions = sessions;
        this.users = users;
        this.capability = capability;
        this.namespace = namespace;
        this.clock = clock;
    }

    /**
     * 一轮问答：事件经 sink 流出（meta → delta* → citations → done，或 error）。
     * 失败不抛异常、不以事件外方式中断——统一以 ChatError 表达。
     * 用户提问在进入流式前落库，此后各条存储各自提交（不把整个流置于单事务内），保证失败可重试时上下文仍在。
     */
    public ChatTurn chat(Optional<Long> sessionIdOpt, String userId, String question, Consumer<ChatEvent> sink) {
        var user = resolveUser(userId);
        if (user == null) {
            sink.accept(new ChatError("unauthorized", "用户未识别，无法发起问答"));
            return new ChatTurn(0, 0, false);
        }
        if (question == null || question.isBlank()) {
            sink.accept(new ChatError("invalid_request", "问题不能为空"));
            return new ChatTurn(0, 0, false);
        }
        var session = resolveSession(sessionIdOpt, user, question);
        if (session == null) {
            sink.accept(new ChatError("session_not_found", "会话不存在或不属于你"));
            return new ChatTurn(0, 0, false);
        }
        var userMessage = sessions.saveMessage(new AiChatMessage(
                0, session.id(), AiChatMessageRole.USER, question.trim(), List.of(), Instant.now(clock)));
        forward(sink, new ChatMeta(Long.toString(session.id()), Long.toString(userMessage.id())));
        var history = historyOf(session.id());
        var request = new ChatRequest(namespace, scopesOf(user), history, question.trim());
        var assistantId = streamAndPersist(session, request, sink);
        return new ChatTurn(session.id(), assistantId, assistantId > 0);
    }

    public List<SessionView> listSessions(String userId) {
        if (userId == null || userId.isBlank()) {
            return List.of();
        }
        return sessions.listSessions(userId.trim()).stream().map(SessionView::from).toList();
    }

    public SessionView renameSession(long sessionId, String userId, String title) {
        var session = requireOwned(sessionId, userId);
        if (title == null || title.isBlank()) {
            throw new AiChatValidationException("会话标题不能为空");
        }
        var now = Instant.now(clock);
        var updated = sessions.updateSession(new AiChatSession(
                session.id(), session.userId(), title.trim(), session.createdAt(), now));
        return SessionView.from(updated);
    }

    public void deleteSession(long sessionId, String userId) {
        requireOwned(sessionId, userId);
        sessions.deleteSession(sessionId);
    }

    public List<MessageView> messages(long sessionId, String userId) {
        requireOwned(sessionId, userId);
        return sessions.listMessages(sessionId).stream().map(MessageView::from).toList();
    }

    private long streamAndPersist(AiChatSession session, ChatRequest request, Consumer<ChatEvent> sink) {
        var answer = new StringBuilder();
        var refs = new ArrayList<AiCitation>();
        long[] assistantId = {0};
        try {
            capability.streamChat(request, event -> {
                switch (event) {
                    case ChatDelta delta -> {
                        answer.append(delta.text());
                        forward(sink, delta);
                    }
                    case ChatCitations citations -> {
                        refs.addAll(citations.refs().stream()
                                .map(reference -> new AiCitation(reference.docId(), reference.location(),
                                        reference.excerpt(), reference.inScope()))
                                .toList());
                        forward(sink, citations);
                    }
                    case ChatDone done -> {
                        var assistant = sessions.saveMessage(new AiChatMessage(
                                0, session.id(), AiChatMessageRole.ASSISTANT, answer.toString(),
                                List.copyOf(refs), Instant.now(clock)));
                        assistantId[0] = assistant.id();
                        sessions.updateSession(new AiChatSession(
                                session.id(), session.userId(), session.title(), session.createdAt(),
                                Instant.now(clock)));
                        forward(sink, done);
                    }
                    case ChatError error -> forward(sink, error);
                    case ChatMeta ignored -> {
                        // 丢弃能力侧 meta，使用本地会话/消息标识
                    }
                }
            });
        } catch (AiCapabilityException exception) {
            forward(sink, new ChatError(codeOf(exception.error()), exception.getMessage()));
            return 0;
        }
        return assistantId[0];
    }

    private void forward(Consumer<ChatEvent> sink, ChatEvent event) {
        if (sink != null) {
            sink.accept(event);
        }
    }

    private SystemUser resolveUser(String userId) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        return users.findByUserId(userId.trim()).orElse(null);
    }

    private AiChatSession resolveSession(Optional<Long> sessionIdOpt, SystemUser user, String question) {
        if (sessionIdOpt == null || sessionIdOpt.isEmpty()) {
            var now = Instant.now(clock);
            var title = question.trim();
            if (title.length() > MAX_TITLE_LENGTH) {
                title = title.substring(0, MAX_TITLE_LENGTH);
            }
            return sessions.saveSession(new AiChatSession(0, user.userId(), title, now, now));
        }
        var session = sessions.findSession(sessionIdOpt.get()).orElse(null);
        if (session == null || !session.userId().equals(user.userId())) {
            return null;
        }
        return session;
    }

    /** 用户 AssetScope 检索边界（并集）：空范围=不受限（发空列表）；否则逐个映射为检索范围。 */
    private List<AiTargetScope> scopesOf(SystemUser user) {
        return user.scopes().stream()
                .map(scope -> new AiTargetScope("", "", scope.productLine(), scope.base(), "", ""))
                .toList();
    }

    private List<ChatMessage> historyOf(long sessionId) {
        var messages = sessions.listMessages(sessionId);
        // 末尾一条是刚落库的当前用户提问，不作为历史发送
        var prior = messages.size() <= 1 ? List.<AiChatMessage>of()
                : messages.subList(0, messages.size() - 1);
        var from = Math.max(0, prior.size() - MAX_HISTORY_TURNS * 2);
        return prior.subList(from, prior.size()).stream()
                .map(message -> new ChatMessage(
                        message.role() == AiChatMessageRole.USER ? "user" : "assistant",
                        message.content()))
                .toList();
    }

    private AiChatSession requireOwned(long sessionId, String userId) {
        if (userId == null || userId.isBlank()) {
            throw new AiChatSessionNotFoundException("会话不存在");
        }
        var session = sessions.findSession(sessionId)
                .orElseThrow(() -> new AiChatSessionNotFoundException("会话不存在"));
        if (!session.userId().equals(userId.trim())) {
            throw new AiChatSessionNotFoundException("会话不存在");
        }
        return session;
    }

    private static String codeOf(AiCapabilityException.AiCapabilityError error) {
        return switch (error) {
            case AUTH_FAILED -> "auth_failed";
            case TIMEOUT -> "timeout";
            case UNAVAILABLE -> "unavailable";
            case PROTOCOL -> "protocol";
        };
    }

    public record ChatTurn(long sessionId, long assistantMessageId, boolean succeeded) {
    }

    public record SessionView(long id, String title, Instant createdAt, Instant updatedAt) {

        public static SessionView from(AiChatSession session) {
            return new SessionView(session.id(), session.title(), session.createdAt(), session.updatedAt());
        }
    }

    public record MessageView(
            long id,
            AiChatMessageRole role,
            String content,
            List<AiCitation> citations,
            Instant createdAt) {

        public static MessageView from(AiChatMessage message) {
            return new MessageView(message.id(), message.role(), message.content(),
                    message.citations(), message.createdAt());
        }
    }
}
