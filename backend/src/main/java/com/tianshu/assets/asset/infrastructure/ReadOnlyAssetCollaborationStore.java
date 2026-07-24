package com.tianshu.assets.asset.infrastructure;

import com.tianshu.assets.asset.application.AssetCollaborationStore;
import com.tianshu.assets.asset.domain.AssetComment;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("oceanbase")
public class ReadOnlyAssetCollaborationStore implements AssetCollaborationStore {

    @Override
    public boolean isFavorite(long assetId, String userId) {
        return false;
    }

    @Override
    public boolean setFavorite(long assetId, String userId, boolean favorite) {
        throw readOnly();
    }

    @Override
    public List<Long> favoriteAssetIds(String userId) {
        return List.of();
    }

    @Override
    public List<StoredComment> comments(long assetId, String userId) {
        return List.of();
    }

    @Override
    public AssetComment addComment(
            long assetId, String userId, String authorName, String content, List<String> imageKeys) {
        throw readOnly();
    }

    @Override
    public void deleteComment(long assetId, long commentId) {
        throw readOnly();
    }

    @Override
    public CommentLikeState setCommentLike(long assetId, long commentId, String userId, boolean liked) {
        throw readOnly();
    }

    @Override
    public boolean isCommentImageLinked(long assetId, String storageKey) {
        return false;
    }

    private UnsupportedOperationException readOnly() {
        return new UnsupportedOperationException("OceanBase 只读适配器未启用协作数据写入");
    }
}
