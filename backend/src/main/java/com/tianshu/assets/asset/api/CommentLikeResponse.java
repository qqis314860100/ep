package com.tianshu.assets.asset.api;

public record CommentLikeResponse(long commentId, boolean liked, long likeCount) {}
