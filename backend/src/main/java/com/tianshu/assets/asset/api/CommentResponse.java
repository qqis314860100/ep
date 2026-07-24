package com.tianshu.assets.asset.api;

import com.tianshu.assets.asset.application.AssetWriteService;
import java.time.Instant;
import java.util.List;

public record CommentResponse(
        long id,
        long assetId,
        String authorId,
        String authorName,
        String content,
        List<CommentImageResponse> images,
        Instant createdAt,
        boolean deleted,
        long likeCount,
        boolean likedByCurrentUser,
        boolean canDelete) {

    public static CommentResponse from(AssetWriteService.CommentView view) {
        var comment = view.comment();
        return new CommentResponse(
                comment.id(),
                comment.assetId(),
                comment.authorId(),
                comment.authorName(),
                comment.content(),
                comment.imageKeys().stream()
                        .map(key -> new CommentImageResponse(
                                key,
                                "/api/v1/assets/" + comment.assetId() + "/comments/images/" + key))
                        .toList(),
                comment.createdAt(),
                comment.deleted(),
                comment.likeCount(),
                view.likedByCurrentUser(),
                view.canDelete());
    }

    public record CommentImageResponse(String key, String url) {}
}
