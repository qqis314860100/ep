package com.tianshu.assets.ai.infrastructure;

import com.tianshu.assets.ai.domain.AiChatMessage;
import com.tianshu.assets.ai.domain.AiChatSession;
import com.tianshu.assets.ai.domain.AiChatRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryAiChatRepository implements AiChatRepository {

    private final Map<Long, AiChatSession> sessions = new ConcurrentHashMap<>();
    private final List<AiChatMessage> messages = new ArrayList<>();
    private final AtomicLong nextSessionId = new AtomicLong(1);
    private final AtomicLong nextMessageId = new AtomicLong(1);

    @Override
    public synchronized AiChatSession saveSession(AiChatSession session) {
        var saved = new AiChatSession(nextSessionId.getAndIncrement(), session.userId(), session.title(),
                session.createdAt(), session.updatedAt());
        sessions.put(saved.id(), saved);
        return saved;
    }

    @Override
    public synchronized AiChatSession updateSession(AiChatSession session) {
        var previous = sessions.get(session.id());
        if (previous == null) {
            throw new IllegalStateException("会话不存在");
        }
        var updated = new AiChatSession(previous.id(), session.userId(), session.title(),
                previous.createdAt(), session.updatedAt());
        sessions.put(updated.id(), updated);
        return updated;
    }

    @Override
    public synchronized Optional<AiChatSession> findSession(long id) {
        return Optional.ofNullable(sessions.get(id));
    }

    @Override
    public synchronized List<AiChatSession> listSessions(String userId) {
        return sessions.values().stream()
                .filter(session -> session.userId().equals(userId))
                .sorted(Comparator.comparing(AiChatSession::updatedAt).reversed())
                .toList();
    }

    @Override
    public synchronized void deleteSession(long sessionId) {
        sessions.remove(sessionId);
        messages.removeIf(message -> message.sessionId() == sessionId);
    }

    @Override
    public synchronized AiChatMessage saveMessage(AiChatMessage message) {
        var saved = new AiChatMessage(nextMessageId.getAndIncrement(), message.sessionId(), message.role(),
                message.content(), message.citations(), message.createdAt());
        messages.add(saved);
        return saved;
    }

    @Override
    public synchronized List<AiChatMessage> listMessages(long sessionId) {
        return messages.stream()
                .filter(message -> message.sessionId() == sessionId)
                .sorted(Comparator.comparing(AiChatMessage::id))
                .toList();
    }
}
