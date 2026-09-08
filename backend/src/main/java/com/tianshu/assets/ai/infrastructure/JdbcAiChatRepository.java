package com.tianshu.assets.ai.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tianshu.assets.ai.domain.AiChatMessage;
import com.tianshu.assets.ai.domain.AiChatMessageRole;
import com.tianshu.assets.ai.domain.AiChatSession;
import com.tianshu.assets.ai.domain.AiChatRepository;
import com.tianshu.assets.ai.domain.AiCitation;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"local", "oceanbase"})
public class JdbcAiChatRepository implements AiChatRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JdbcAiChatRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public AiChatSession saveSession(AiChatSession session) {
        var keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("""
                    INSERT INTO ai_chat_session (user_id, title, created_at, updated_at)
                    VALUES (?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, session.userId());
            statement.setString(2, session.title());
            statement.setTimestamp(3, Timestamp.from(session.createdAt()));
            statement.setTimestamp(4, Timestamp.from(session.updatedAt()));
            return statement;
        }, keyHolder);
        var id = keyHolder.getKey() == null ? 0L : keyHolder.getKey().longValue();
        return new AiChatSession(id, session.userId(), session.title(), session.createdAt(), session.updatedAt());
    }

    @Override
    public AiChatSession updateSession(AiChatSession session) {
        var updated = jdbc.update("UPDATE ai_chat_session SET title = ?, updated_at = ? WHERE id = ?",
                session.title(), Timestamp.from(session.updatedAt()), session.id());
        if (updated == 0) {
            throw new IllegalStateException("会话不存在");
        }
        return session;
    }

    @Override
    public Optional<AiChatSession> findSession(long id) {
        var sessions = jdbc.query("SELECT * FROM ai_chat_session WHERE id = ?",
                (resultSet, rowNum) -> mapSession(resultSet), id);
        return sessions.stream().findFirst();
    }

    @Override
    public List<AiChatSession> listSessions(String userId) {
        return jdbc.query("SELECT * FROM ai_chat_session WHERE user_id = ? ORDER BY updated_at DESC",
                (resultSet, rowNum) -> mapSession(resultSet), userId);
    }

    @Override
    public void deleteSession(long sessionId) {
        jdbc.update("DELETE FROM ai_chat_message WHERE session_id = ?", sessionId);
        jdbc.update("DELETE FROM ai_chat_session WHERE id = ?", sessionId);
    }

    @Override
    public AiChatMessage saveMessage(AiChatMessage message) {
        var keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("""
                    INSERT INTO ai_chat_message (session_id, role, content, citations, created_at)
                    VALUES (?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, message.sessionId());
            statement.setString(2, message.role().name());
            statement.setString(3, message.content());
            statement.setString(4, writeJson(message.citations()));
            statement.setTimestamp(5, Timestamp.from(message.createdAt()));
            return statement;
        }, keyHolder);
        var id = keyHolder.getKey() == null ? 0L : keyHolder.getKey().longValue();
        return new AiChatMessage(id, message.sessionId(), message.role(), message.content(),
                message.citations(), message.createdAt());
    }

    @Override
    public List<AiChatMessage> listMessages(long sessionId) {
        return jdbc.query("SELECT * FROM ai_chat_message WHERE session_id = ? ORDER BY id",
                (resultSet, rowNum) -> mapMessage(resultSet), sessionId);
    }

    private AiChatSession mapSession(ResultSet resultSet) throws SQLException {
        return new AiChatSession(
                resultSet.getLong("id"),
                resultSet.getString("user_id"),
                resultSet.getString("title"),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant());
    }

    private AiChatMessage mapMessage(ResultSet resultSet) throws SQLException {
        return new AiChatMessage(
                resultSet.getLong("id"),
                resultSet.getLong("session_id"),
                AiChatMessageRole.valueOf(resultSet.getString("role")),
                resultSet.getString("content"),
                readCitations(resultSet.getString("citations")),
                resultSet.getTimestamp("created_at").toInstant());
    }

    private List<AiCitation> readCitations(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("AI 会话引用 JSON 解析失败", exception);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("AI 会话引用 JSON 序列化失败", exception);
        }
    }
}
