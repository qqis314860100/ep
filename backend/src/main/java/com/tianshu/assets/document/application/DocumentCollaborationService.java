package com.tianshu.assets.document.application;

import com.tianshu.assets.asset.application.ForbiddenOperationException;
import com.tianshu.assets.common.file.FileStorage;
import com.tianshu.assets.document.domain.DocumentComment;
import com.tianshu.assets.document.domain.DocumentRepository;
import com.tianshu.assets.document.domain.DocumentStatus;
import com.tianshu.assets.document.domain.KnowledgeDocument;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * 文档协作（收藏/评论带版本上下文/点赞）与我的文档入口。
 */
@Service
public class DocumentCollaborationService {

    private static final Set<String> GOVERNANCE_ROLES = Set.of("CONTENT_ADMIN", "SYSTEM_ADMIN");

    private final DocumentRepository repository;
    private final DocumentCollaborationStore store;
    private final FileStorage fileStorage;

    public DocumentCollaborationService(DocumentRepository repository,
            DocumentCollaborationStore store, FileStorage fileStorage) {
        this.repository = repository;
        this.store = store;
        this.fileStorage = fileStorage;
    }

    public boolean isFavorite(long documentId, String userId) {
        requirePublished(documentId);
        return store.isFavorite(documentId, userId);
    }

    public boolean setFavorite(long documentId, String userId, boolean favorite) {
        requirePublished(documentId);
        return store.setFavorite(documentId, userId, favorite);
    }

    /** 我的收藏文档（已发布与已停用均保留，草稿除外）。 */
    public List<KnowledgeDocument> myFavorites(String userId) {
        return store.favoriteDocumentIds(userId).stream()
                .map(repository::findById)
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .filter(document -> document.status() == DocumentStatus.PUBLISHED
                        || document.status() == DocumentStatus.DISABLED)
                .toList();
    }

    /** 我维护的文档（草稿/已发布/已停用，按状态筛选）。 */
    public List<KnowledgeDocument> myDocuments(String maintainerId, String status) {
        return repository.findAll().stream()
                .filter(document -> document.maintainerId().equals(maintainerId))
                .filter(document -> status == null || status.isBlank() || document.status().name().equalsIgnoreCase(status))
                .sorted((left, right) -> right.updatedAt().compareTo(left.updatedAt()))
                .toList();
    }

    public List<DocumentCollaborationStore.StoredComment> comments(long documentId, String userId) {
        requirePublished(documentId);
        return store.comments(documentId, userId);
    }

    public DocumentComment addComment(long documentId, long versionId, String userId, String authorName,
            String content, List<String> imageKeys) {
        var document = requirePublished(documentId);
        var version = repository.findVersion(documentId, versionId)
                .orElseThrow(() -> new DocumentNotFoundException("文档版本不存在或不可访问"));
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("评论内容不能为空");
        }
        if (imageKeys != null && imageKeys.size() > 6) {
            throw new IllegalArgumentException("评论图片最多 6 张");
        }
        if (imageKeys != null) {
            for (var key : imageKeys) {
                if (fileStorage.open(key).isEmpty()) {
                    throw new IllegalArgumentException("评论图片不存在或不可访问");
                }
            }
        }
        return store.addComment(documentId, version.id(), userId, authorName, content.trim(),
                imageKeys == null ? List.of() : imageKeys);
    }

    public void deleteComment(long documentId, long commentId, String userId, String roles) {
        requirePublished(documentId);
        var comment = store.comments(documentId, userId).stream()
                .map(DocumentCollaborationStore.StoredComment::comment)
                .filter(item -> item.id() == commentId)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("评论不存在"));
        var isAdmin = roles != null && java.util.Arrays.stream(roles.split(","))
                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                .anyMatch(GOVERNANCE_ROLES::contains);
        if (!comment.authorId().equals(userId) && !isAdmin) {
            throw new ForbiddenOperationException("只有作者或管理员可以删除评论");
        }
        store.deleteComment(documentId, commentId);
    }

    public DocumentCollaborationStore.CommentLikeState setCommentLike(long documentId, long commentId,
            String userId, boolean liked) {
        requirePublished(documentId);
        return store.setCommentLike(documentId, commentId, userId, liked);
    }

    private KnowledgeDocument requirePublished(long documentId) {
        return repository.findById(documentId)
                .filter(document -> document.status() == DocumentStatus.PUBLISHED)
                .orElseThrow(() -> new DocumentNotFoundException("文档不存在或不可访问"));
    }
}
