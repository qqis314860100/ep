package com.tianshu.assets.ai.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tianshu.assets.ai.domain.AiProposedFields;
import com.tianshu.assets.ai.domain.AiSuggestion;
import com.tianshu.assets.ai.domain.AiSuggestionRepository;
import com.tianshu.assets.ai.domain.AiSuggestionStatus;
import com.tianshu.assets.ai.domain.AiSuggestionTargetType;
import com.tianshu.assets.ai.domain.AiTargetScope;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"local", "oceanbase"})
public class JdbcAiSuggestionRepository implements AiSuggestionRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JdbcAiSuggestionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public AiSuggestion save(AiSuggestion suggestion) {
        var keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("""
                    INSERT INTO ai_suggestion
                        (target_type, target_id, target_title, status, source, proposed, evidence, confidence,
                         scopes, created_by, created_at, resolved_by, resolved_at, version)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                    """, Statement.RETURN_GENERATED_KEYS);
            bind(statement, suggestion);
            return statement;
        }, keyHolder);
        var key = keyHolder.getKey() == null ? 0L : keyHolder.getKey().longValue();
        return withId(suggestion, key, 0);
    }

    @Override
    public AiSuggestion update(AiSuggestion suggestion, long expectedVersion) {
        var updated = jdbc.update("""
                UPDATE ai_suggestion
                   SET status = ?, resolved_by = ?, resolved_at = ?, version = version + 1
                 WHERE id = ? AND version = ?
                """,
                suggestion.status().name(),
                suggestion.resolvedBy(),
                suggestion.resolvedAt() == null ? null : Timestamp.from(suggestion.resolvedAt()),
                suggestion.id(),
                expectedVersion);
        if (updated == 0) {
            throw new IllegalStateException("版本不匹配");
        }
        return withId(suggestion, suggestion.id(), expectedVersion + 1);
    }

    @Override
    public Optional<AiSuggestion> findById(long id) {
        return query("SELECT * FROM ai_suggestion WHERE id = ?", id).stream().findFirst();
    }

    @Override
    public List<AiSuggestion> findByTarget(AiSuggestionTargetType targetType, long targetId) {
        return query("SELECT * FROM ai_suggestion WHERE target_type = ? AND target_id = ? ORDER BY id",
                targetType.name(), targetId);
    }

    @Override
    public List<AiSuggestion> findAll() {
        return query("SELECT * FROM ai_suggestion ORDER BY id");
    }

    private List<AiSuggestion> query(String sql, Object... args) {
        return jdbc.query(sql, (resultSet, rowNum) -> map(resultSet), args);
    }

    private AiSuggestion map(ResultSet resultSet) throws SQLException {
        var createdAt = resultSet.getTimestamp("created_at");
        var resolvedAt = resultSet.getTimestamp("resolved_at");
        return new AiSuggestion(
                resultSet.getLong("id"),
                AiSuggestionTargetType.valueOf(resultSet.getString("target_type")),
                resultSet.getLong("target_id"),
                resultSet.getString("target_title"),
                AiSuggestionStatus.valueOf(resultSet.getString("status")),
                resultSet.getString("source"),
                readJson(resultSet.getString("proposed"), AiProposedFields.class),
                List.of(readJson(resultSet.getString("evidence"), String[].class)),
                (Double) resultSet.getObject("confidence"),
                List.of(readJson(resultSet.getString("scopes"), AiTargetScope[].class)),
                resultSet.getString("created_by"),
                createdAt == null ? null : createdAt.toInstant(),
                resultSet.getString("resolved_by"),
                resolvedAt == null ? null : resolvedAt.toInstant(),
                resultSet.getLong("version"));
    }

    private void bind(PreparedStatement statement, AiSuggestion suggestion) throws SQLException {
        statement.setString(1, suggestion.targetType().name());
        statement.setLong(2, suggestion.targetId());
        statement.setString(3, suggestion.targetTitle());
        statement.setString(4, suggestion.status().name());
        statement.setString(5, suggestion.source());
        statement.setString(6, writeJson(suggestion.proposed()));
        statement.setString(7, writeJson(suggestion.evidence()));
        statement.setObject(8, suggestion.confidence());
        statement.setString(9, writeJson(suggestion.scopes()));
        statement.setString(10, suggestion.createdBy());
        statement.setTimestamp(11, suggestion.createdAt() == null ? null : Timestamp.from(suggestion.createdAt()));
        statement.setString(12, suggestion.resolvedBy());
        statement.setTimestamp(13, suggestion.resolvedAt() == null ? null : Timestamp.from(suggestion.resolvedAt()));
    }

    private AiSuggestion withId(AiSuggestion suggestion, long id, long version) {
        return new AiSuggestion(id, suggestion.targetType(), suggestion.targetId(), suggestion.targetTitle(),
                suggestion.status(), suggestion.source(), suggestion.proposed(), suggestion.evidence(),
                suggestion.confidence(), suggestion.scopes(), suggestion.createdBy(), suggestion.createdAt(),
                suggestion.resolvedBy(), suggestion.resolvedAt(), version);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("AI 建议 JSON 序列化失败", exception);
        }
    }

    private <T> T readJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("AI 建议 JSON 解析失败", exception);
        }
    }
}
