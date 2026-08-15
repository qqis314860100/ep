package com.tianshu.assets.document.domain;

import java.util.List;

/** 知识文档协作（收藏/评论/点赞），评论记录评论时的文档版本。 */
public record DocumentComment(
        long id,
        long documentId,
        long versionId,
        String authorId,
        String authorName,
        String content,
        List<String> imageKeys,
        long likeCount,
        boolean deleted,
        String createdAt) {

    public DocumentComment {
        content = content == null ? "" : content.trim();
        imageKeys = imageKeys == null ? List.of() : List.copyOf(imageKeys);
        authorId = authorId == null ? "" : authorId.trim();
        authorName = authorName == null ? "" : authorName.trim();
    }
}
