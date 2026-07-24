package com.tianshu.assets.asset.infrastructure;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tianshu.assets.asset.application.AssetCollaborationStore;
import com.tianshu.assets.asset.application.AssetNotFoundException;
import com.tianshu.assets.asset.domain.AssetComment;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("local")
public class JdbcAssetCollaborationStore implements AssetCollaborationStore {

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    public JdbcAssetCollaborationStore(JdbcClient jdbcClient, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean isFavorite(long assetId, String userId) {
        return jdbcClient.sql("SELECT COUNT(*) FROM sys_drawing_collect WHERE drawing_id = :assetId AND created_by = :userId")
                .param("assetId", assetId).param("userId", userNumber(userId, userId)).query(Long.class).single() > 0;
    }

    @Override
    @Transactional
    public boolean setFavorite(long assetId, String userId, boolean favorite) {
        var userNumber = userNumber(userId, userId);
        if (favorite) {
            jdbcClient.sql("""
                    INSERT IGNORE INTO sys_drawing_collect
                        (drawing_id, created_by, created_by_name, last_updated_by, last_updated_by_name)
                    VALUES (:assetId, :userId, :userName, :userId, :userName)
                    """).param("assetId", assetId).param("userId", userNumber)
                    .param("userName", userId).update();
        } else {
            jdbcClient.sql("DELETE FROM sys_drawing_collect WHERE drawing_id = :assetId AND created_by = :userId")
                    .param("assetId", assetId).param("userId", userNumber).update();
        }
        return favorite;
    }

    @Override
    public List<Long> favoriteAssetIds(String userId) {
        return jdbcClient.sql("SELECT drawing_id FROM sys_drawing_collect WHERE created_by = :userId ORDER BY creation_date DESC")
                .param("userId", userNumber(userId, userId)).query(Long.class).list();
    }

    @Override
    public List<StoredComment> comments(long assetId, String userId) {
        var userNumber = userNumber(userId, userId);
        return jdbcClient.sql("""
                SELECT comment.id, comment.drawing_id, person.code AS author_id, comment.created_by_name,
                       comment.comment_content, comment.comment_img, comment.creation_date, comment.deleted_at,
                       comment.like_count, CASE WHEN user_like.id IS NULL THEN 0 ELSE 1 END AS liked
                FROM sys_drawing_comment comment
                LEFT JOIN temp_person person ON person.id = comment.created_by
                LEFT JOIN sys_drawing_comment_like user_like
                       ON user_like.comment_id = comment.id AND user_like.created_by = :userId
                WHERE comment.drawing_id = :assetId
                ORDER BY comment.creation_date DESC, comment.id DESC
                """).param("userId", userNumber).param("assetId", assetId).query((rs, ignored) -> {
                    var authorId = rs.getString("author_id");
                    var createdAt = rs.getTimestamp("creation_date");
                    var comment = new AssetComment(rs.getLong("id"), rs.getLong("drawing_id"),
                            authorId == null ? String.valueOf(rs.getLong("id")) : authorId,
                            rs.getString("created_by_name"), nullable(rs.getString("comment_content")),
                            parseStrings(rs.getString("comment_img")),
                            createdAt == null ? Instant.now() : createdAt.toInstant(),
                            rs.getTimestamp("deleted_at") != null, rs.getLong("like_count"));
                    return new StoredComment(comment, rs.getBoolean("liked"));
                }).list();
    }

    @Override
    @Transactional
    public AssetComment addComment(
            long assetId, String userId, String authorName, String content, List<String> imageKeys) {
        var userNumber = userNumber(userId, authorName);
        var imageJson = writeJson(imageKeys);
        jdbcClient.sql("""
                INSERT INTO sys_drawing_comment
                    (drawing_id, comment_img, comment_content, created_by, created_by_name,
                     last_updated_by, last_updated_by_name)
                VALUES (:assetId, :images, :content, :userId, :authorName, :userId, :authorName)
                """).param("assetId", assetId).param("images", imageJson).param("content", content)
                .param("userId", userNumber).param("authorName", authorName).update();
        var id = jdbcClient.sql("SELECT LAST_INSERT_ID()").query(Long.class).single();
        return new AssetComment(id, assetId, normalizeUser(userId), authorName, content, imageKeys,
                Instant.now(), false, 0);
    }

    @Override
    public void deleteComment(long assetId, long commentId) {
        var updated = jdbcClient.sql("""
                UPDATE sys_drawing_comment SET deleted_at = CURRENT_TIMESTAMP, comment_content = '', comment_img = NULL
                WHERE id = :commentId AND drawing_id = :assetId AND deleted_at IS NULL
                """).param("commentId", commentId).param("assetId", assetId).update();
        if (updated == 0) throw new AssetNotFoundException(commentId);
    }

    @Override
    @Transactional
    public CommentLikeState setCommentLike(long assetId, long commentId, String userId, boolean liked) {
        var userNumber = userNumber(userId, userId);
        if (liked) {
            jdbcClient.sql("""
                    INSERT IGNORE INTO sys_drawing_comment_like
                        (comment_id, created_by, created_by_name, last_updated_by, last_updated_by_name)
                    VALUES (:commentId, :userId, :userName, :userId, :userName)
                    """).param("commentId", commentId).param("userId", userNumber)
                    .param("userName", userId).update();
        } else {
            jdbcClient.sql("DELETE FROM sys_drawing_comment_like WHERE comment_id = :commentId AND created_by = :userId")
                    .param("commentId", commentId).param("userId", userNumber).update();
        }
        var count = jdbcClient.sql("SELECT COUNT(*) FROM sys_drawing_comment_like WHERE comment_id = :commentId")
                .param("commentId", commentId).query(Long.class).single();
        var updated = jdbcClient.sql("UPDATE sys_drawing_comment SET like_count = :count WHERE id = :commentId AND drawing_id = :assetId AND deleted_at IS NULL")
                .param("count", count).param("commentId", commentId).param("assetId", assetId).update();
        if (updated == 0) throw new AssetNotFoundException(commentId);
        return new CommentLikeState(liked, count);
    }

    @Override
    public boolean isCommentImageLinked(long assetId, String storageKey) {
        return comments(assetId, "demo-user").stream().anyMatch(stored -> !stored.comment().deleted()
                && stored.comment().imageKeys().contains(storageKey));
    }

    private long userNumber(String userId, String displayName) {
        var normalized = normalizeUser(userId);
        var existing = jdbcClient.sql("SELECT id FROM temp_person WHERE code = :code")
                .param("code", normalized).query(Long.class).optional();
        if (existing.isPresent()) return existing.get();
        jdbcClient.sql("""
                INSERT IGNORE INTO temp_person (code, name, created_by, last_updated_by)
                VALUES (:code, :name, -1, -1)
                """).param("code", normalized)
                .param("name", displayName == null || displayName.isBlank() ? normalized : displayName).update();
        return jdbcClient.sql("SELECT id FROM temp_person WHERE code = :code")
                .param("code", normalized).query(Long.class).single();
    }

    private List<String> parseStrings(String json) {
        try {
            return json == null || json.isBlank() ? List.of() : objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception exception) {
            return List.of();
        }
    }

    private String writeJson(List<String> values) {
        try {
            return values == null || values.isEmpty() ? null : objectMapper.writeValueAsString(values);
        } catch (Exception exception) {
            throw new IllegalArgumentException("评论图片信息无法保存", exception);
        }
    }

    private String normalizeUser(String userId) {
        return userId == null || userId.isBlank() ? "demo-user" : userId;
    }

    private String nullable(String value) {
        return value == null ? "" : value;
    }
}
