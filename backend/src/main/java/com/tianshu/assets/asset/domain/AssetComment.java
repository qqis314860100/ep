package com.tianshu.assets.asset.domain;

import java.time.Instant;
import java.util.List;

public record AssetComment(
        long id,
        long assetId,
        String authorId,
        String authorName,
        String content,
        List<String> imageKeys,
        Instant createdAt,
        boolean deleted,
        long likeCount) {

    public AssetComment {
        imageKeys = imageKeys == null ? List.of() : List.copyOf(imageKeys);
    }
}
