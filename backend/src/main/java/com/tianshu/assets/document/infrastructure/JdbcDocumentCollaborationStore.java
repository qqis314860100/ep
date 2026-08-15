package com.tianshu.assets.document.infrastructure;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tianshu.assets.document.application.DocumentCollaborationStore;
import com.tianshu.assets.document.domain.DocumentComment;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile({"local", "oceanbase"})
public class JdbcDocumentCollaborationStore implements DocumentCollaborationStore {

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    public JdbcDocumentCollaborationStore(JdbcClient jdbcClient, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean isFavorite(long documentId, String userId) {
        return jdbcClient.sql("SELECT COUNT(*) FROM document_favorite WHERE document_id = :documentId AND user_id = :userId")
                .param("documentId", documentId).param("userId", userId).query(Long.class).single() > 0;
    }

    @Override
    @Transactional
    public boolean setFavorite(long documentId, String userId, boolean favorite) {
        if (favorite) {
            jdbcClient.sql("INSERT IGNORE INTO document_favorite (document_id, user_id) VALUES (:documentId, :userId)")
                    .param("documentId", documentId).param("userId", userId).update();
        } else {
            jdbcClient.sql("DELETE FROM document_favorite WHERE document_id = :documentId AND user_id = :userId")
                    .param("documentId", documentId).param("userId", userId).update();
        }
        return favorite;
    }

    @Override
    public List<Long> favoriteDocumentIds(String userId) {
        return jdbcClient.sql("SELECT document_id FROM document_favorite WHERE user_id = :userId ORDER BY created_at DESC")
                .param("userId", userId).query(Long.class).list();
    }

    @Override
    public List<StoredComment> comments(long documentId, String userId) {
        return jdbcClient.sql("""
                SELECT c.id, c.document_id, c.version_id, c.author_id, c.author_name, c.content,
                       c.image_keys_json, c.like_count, c.deleted, c.created_at,
                       EXISTS (SELECT 1 FROM document_comment_like l WHERE l.comment_id = c.id AND l.user_id = :userId) AS liked
                FROM document_comment c
                WHERE c.document_id = :documentId
                ORDER BY c.id DESC
                """).param("documentId", documentId).param("userId", userId)
                .query((rs, row) -> new StoredComment(
                        new DocumentComment(rs.getLong("id"), rs.getLong("document_id"), rs.getLong("version_id"),
                                rs.getString("author_id"), rs.getString("author_name"), rs.getString("content"),
                                imageKeys(rs.getString("image_keys_json")), rs.getLong("like_count"),
                                rs.getBoolean("deleted"), rs.getTimestamp("created_at").toInstant().toString()),
                        rs.getBoolean("liked")))
                .list();
    }

    @Override
    @Transactional
    public DocumentComment addComment(long documentId, long versionId, String userId, String authorName,
            String content, List<String> imageKeys) {
        var key = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbcClient.sql("""
                INSERT INTO document_comment
                    (document_id, version_id, author_id, author_name, content, image_keys_json, like_count, deleted)
                VALUES (:documentId, :versionId, :userId, :authorName, :content, :imageKeys, 0, 0)
                """).param("documentId", documentId).param("versionId", versionId).param("userId", userId)
                .param("authorName", authorName).param("content", content).param("imageKeys", json(imageKeys))
                .update(key, "id");
        var id = key.getKeyAs(Long.class);
        return comments(documentId, userId).stream().map(StoredComment::comment)
                .filter(comment -> comment.id() == id).findFirst().orElseThrow();
    }

    @Override
    @Transactional
    public void deleteComment(long documentId, long commentId) {
        jdbcClient.sql("UPDATE document_comment SET deleted = 1 WHERE id = :id AND document_id = :documentId")
                .param("id", commentId).param("documentId", documentId).update();
    }

    @Override
    @Transactional
    public CommentLikeState setCommentLike(long documentId, long commentId, String userId, boolean liked) {
        if (liked) {
            jdbcClient.sql("INSERT IGNORE INTO document_comment_like (comment_id, user_id) VALUES (:commentId, :userId)")
                    .param("commentId", commentId).param("userId", userId).update();
        } else {
            jdbcClient.sql("DELETE FROM document_comment_like WHERE comment_id = :commentId AND user_id = :userId")
                    .param("commentId", commentId).param("userId", userId).update();
        }
        var count = jdbcClient.sql("SELECT COUNT(*) FROM document_comment_like WHERE comment_id = :commentId")
                .param("commentId", commentId).query(Long.class).single();
        jdbcClient.sql("UPDATE document_comment SET like_count = :count WHERE id = :commentId")
                .param("count", count).param("commentId", commentId).update();
        return new CommentLikeState(liked, count);
    }

    private List<String> imageKeys(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception exception) {
            return List.of();
        }
    }

    private String json(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (Exception exception) {
            return "[]";
        }
    }
}
