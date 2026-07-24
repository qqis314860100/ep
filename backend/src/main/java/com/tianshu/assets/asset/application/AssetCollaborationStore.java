package com.tianshu.assets.asset.application;

import com.tianshu.assets.asset.domain.AssetComment;
import java.util.List;

public interface AssetCollaborationStore {

    boolean isFavorite(long assetId, String userId);

    boolean setFavorite(long assetId, String userId, boolean favorite);

    List<Long> favoriteAssetIds(String userId);

    List<StoredComment> comments(long assetId, String userId);

    AssetComment addComment(long assetId, String userId, String authorName, String content, List<String> imageKeys);

    void deleteComment(long assetId, long commentId);

    CommentLikeState setCommentLike(long assetId, long commentId, String userId, boolean liked);

    boolean isCommentImageLinked(long assetId, String storageKey);

    record StoredComment(AssetComment comment, boolean likedByCurrentUser) {}

    record CommentLikeState(boolean liked, long likeCount) {}
}
