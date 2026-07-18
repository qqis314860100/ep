package com.tianshu.assets.asset.api;

import com.tianshu.assets.asset.domain.AssetComment;
import java.time.Instant;

public record CommentResponse(
        long id,
        long assetId,
        String authorName,
        String content,
        Instant createdAt,
        boolean deleted,
        long likeCount) {

    public static CommentResponse from(AssetComment comment) {
        return new CommentResponse(
                comment.id(),
                comment.assetId(),
                comment.authorName(),
                comment.content(),
                comment.createdAt(),
                comment.deleted(),
                comment.likeCount());
    }
}
