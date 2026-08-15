package com.tianshu.assets.document.infrastructure;

import com.tianshu.assets.document.application.DocumentCollaborationStore;
import com.tianshu.assets.document.domain.DocumentComment;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("dev")
public class InMemoryDocumentCollaborationStore implements DocumentCollaborationStore {

    private final Map<Long, Map<String, Instant>> favorites = new ConcurrentHashMap<>();
    private final Map<Long, List<DocumentComment>> comments = new ConcurrentHashMap<>();
    private final Map<Long, Map<String, Instant>> commentLikes = new ConcurrentHashMap<>();
    private final AtomicLong nextCommentId = new AtomicLong(1);

    @Override
    public boolean isFavorite(long documentId, String userId) {
        return favorites.getOrDefault(documentId, Map.of()).containsKey(userId);
    }

    @Override
    public synchronized boolean setFavorite(long documentId, String userId, boolean favorite) {
        var users = favorites.computeIfAbsent(documentId, key -> new ConcurrentHashMap<>());
        if (favorite) users.put(userId, Instant.now());
        else users.remove(userId);
        return favorite;
    }

    @Override
    public List<Long> favoriteDocumentIds(String userId) {
        return favorites.entrySet().stream()
                .filter(entry -> entry.getValue().containsKey(userId))
                .map(Map.Entry::getKey)
                .toList();
    }

    @Override
    public synchronized List<StoredComment> comments(long documentId, String userId) {
        return comments.getOrDefault(documentId, List.of()).stream()
                .sorted(Comparator.comparing(DocumentComment::createdAt).reversed())
                .map(comment -> new StoredComment(comment, isLiked(comment.id(), userId)))
                .toList();
    }

    @Override
    public synchronized DocumentComment addComment(long documentId, long versionId, String userId,
            String authorName, String content, List<String> imageKeys) {
        var created = new DocumentComment(nextCommentId.getAndIncrement(), documentId, versionId,
                userId, authorName, content, imageKeys, 0, false, Instant.now().toString());
        comments.computeIfAbsent(documentId, key -> new ArrayList<>()).add(created);
        return created;
    }

    @Override
    public synchronized void deleteComment(long documentId, long commentId) {
        comments.getOrDefault(documentId, List.of()).stream()
                .filter(comment -> comment.id() == commentId)
                .findFirst()
                .ifPresent(comment -> replace(commentId, new DocumentComment(comment.id(), comment.documentId(),
                        comment.versionId(), comment.authorId(), comment.authorName(), comment.content(),
                        comment.imageKeys(), comment.likeCount(), true, comment.createdAt())));
    }

    @Override
    public synchronized CommentLikeState setCommentLike(long documentId, long commentId, String userId, boolean liked) {
        var current = comments.getOrDefault(documentId, List.of()).stream()
                .filter(comment -> comment.id() == commentId)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("评论不存在：" + commentId));
        var likes = commentLikes.computeIfAbsent(commentId, key -> new ConcurrentHashMap<>());
        if (liked) likes.put(userId, Instant.now());
        else likes.remove(userId);
        var likeCount = likes.size();
        replace(commentId, new DocumentComment(current.id(), current.documentId(), current.versionId(),
                current.authorId(), current.authorName(), current.content(), current.imageKeys(),
                likeCount, current.deleted(), current.createdAt()));
        return new CommentLikeState(liked, likeCount);
    }

    private boolean isLiked(long commentId, String userId) {
        return commentLikes.getOrDefault(commentId, Map.of()).containsKey(userId);
    }

    private void replace(long commentId, DocumentComment updated) {
        var list = comments.getOrDefault(updated.documentId(), List.of());
        var copy = new ArrayList<>(list);
        for (var index = 0; index < copy.size(); index++) {
            if (copy.get(index).id() == commentId) {
                copy.set(index, updated);
                comments.put(updated.documentId(), copy);
                return;
            }
        }
    }

    @SuppressWarnings("unused")
    private Optional<DocumentComment> find(long documentId, long commentId) {
        return comments.getOrDefault(documentId, List.of()).stream()
                .filter(comment -> comment.id() == commentId)
                .findFirst();
    }
}
