package com.tianshu.assets.asset.domain;

import java.time.Instant;

public record AssetComment(
        long id,
        long assetId,
        String authorId,
        String authorName,
        String content,
        Instant createdAt,
        boolean deleted,
        long likeCount) {}
