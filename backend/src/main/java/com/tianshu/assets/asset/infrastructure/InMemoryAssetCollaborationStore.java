package com.tianshu.assets.asset.infrastructure;

import com.tianshu.assets.asset.application.AssetCollaborationStore;
import com.tianshu.assets.asset.application.AssetNotFoundException;
import com.tianshu.assets.asset.domain.AssetComment;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
public class InMemoryAssetCollaborationStore implements AssetCollaborationStore {

    private final Set<String> favorites = ConcurrentHashMap.newKeySet();
    private final List<AssetComment> comments = new ArrayList<>();
    private final Map<String, Set<Long>> commentLikes = new ConcurrentHashMap<>();
    private final AtomicLong nextCommentId = new AtomicLong(1);

    @Override
    public boolean isFavorite(long assetId, String userId) {
        return favorites.contains(key(assetId, userId));
    }

    @Override
    public boolean setFavorite(long assetId, String userId, boolean favorite) {
        if (favorite) favorites.add(key(assetId, userId));
        else favorites.remove(key(assetId, userId));
        return favorite;
    }

    @Override
    public List<Long> favoriteAssetIds(String userId) {
        var prefix = normalizeUser(userId) + ":";
        return favorites.stream().filter(value -> value.startsWith(prefix))
                .map(value -> Long.parseLong(value.substring(prefix.length())))
                .sorted().toList();
    }

    @Override
    public synchronized List<StoredComment> comments(long assetId, String userId) {
        var likes = commentLikes.getOrDefault(normalizeUser(userId), Set.of());
        return comments.stream().filter(comment -> comment.assetId() == assetId)
                .sorted((left, right) -> right.createdAt().compareTo(left.createdAt()))
                .map(comment -> new StoredComment(comment, likes.contains(comment.id())))
                .toList();
    }

    @Override
    public synchronized AssetComment addComment(
            long assetId, String userId, String authorName, String content, List<String> imageKeys) {
        var normalizedUser = normalizeUser(userId);
        var comment = new AssetComment(nextCommentId.getAndIncrement(), assetId, normalizedUser,
                authorName == null || authorName.isBlank() ? normalizedUser : authorName, content, imageKeys,
                Instant.now(), false, 0);
        comments.add(comment);
        return comment;
    }

    @Override
    public synchronized void deleteComment(long assetId, long commentId) {
        var comment = findComment(assetId, commentId);
        comments.set(comments.indexOf(comment), new AssetComment(comment.id(), comment.assetId(),
                comment.authorId(), comment.authorName(), comment.content(), comment.imageKeys(),
                comment.createdAt(), true, comment.likeCount()));
    }

    @Override
    public synchronized CommentLikeState setCommentLike(long assetId, long commentId, String userId, boolean liked) {
        var comment = findComment(assetId, commentId);
        var userLikes = commentLikes.computeIfAbsent(normalizeUser(userId), ignored -> ConcurrentHashMap.newKeySet());
        if (liked) userLikes.add(commentId);
        else userLikes.remove(commentId);
        var count = commentLikes.values().stream().filter(likes -> likes.contains(commentId)).count();
        comments.set(comments.indexOf(comment), new AssetComment(comment.id(), comment.assetId(),
                comment.authorId(), comment.authorName(), comment.content(), comment.imageKeys(),
                comment.createdAt(), comment.deleted(), count));
        return new CommentLikeState(liked, count);
    }

    @Override
    public synchronized boolean isCommentImageLinked(long assetId, String storageKey) {
        return comments.stream().anyMatch(comment -> comment.assetId() == assetId && !comment.deleted()
                && comment.imageKeys().contains(storageKey));
    }

    private AssetComment findComment(long assetId, long commentId) {
        return comments.stream().filter(comment -> comment.assetId() == assetId && comment.id() == commentId)
                .findFirst().orElseThrow(() -> new AssetNotFoundException(commentId));
    }

    private String key(long assetId, String userId) {
        return normalizeUser(userId) + ":" + assetId;
    }

    private String normalizeUser(String userId) {
        return userId == null || userId.isBlank() ? "demo-user" : userId;
    }
}
