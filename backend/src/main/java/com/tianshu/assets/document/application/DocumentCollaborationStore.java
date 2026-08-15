package com.tianshu.assets.document.application;

import com.tianshu.assets.document.domain.DocumentComment;
import java.util.List;

public interface DocumentCollaborationStore {

    boolean isFavorite(long documentId, String userId);

    boolean setFavorite(long documentId, String userId, boolean favorite);

    List<Long> favoriteDocumentIds(String userId);

    List<StoredComment> comments(long documentId, String userId);

    DocumentComment addComment(long documentId, long versionId, String userId, String authorName,
            String content, List<String> imageKeys);

    void deleteComment(long documentId, long commentId);

    CommentLikeState setCommentLike(long documentId, long commentId, String userId, boolean liked);

    record StoredComment(DocumentComment comment, boolean likedByCurrentUser) {}

    record CommentLikeState(boolean liked, long likeCount) {}
}
