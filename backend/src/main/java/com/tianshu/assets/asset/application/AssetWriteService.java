package com.tianshu.assets.asset.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tianshu.assets.asset.domain.Asset;
import com.tianshu.assets.asset.domain.AssetComment;
import com.tianshu.assets.asset.domain.AssetFile;
import com.tianshu.assets.asset.domain.AssetRepository;
import com.tianshu.assets.asset.domain.AssetScope;
import com.tianshu.assets.asset.domain.AssetStatus;
import com.tianshu.assets.system.domain.OperationLog;
import com.tianshu.assets.system.domain.OperationLogStore;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Collection;
import com.tianshu.assets.asset.infrastructure.InMemoryAssetCollaborationStore;
import com.tianshu.assets.system.infrastructure.InMemoryOperationLogStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AssetWriteService {

    private static final java.util.Set<String> GOVERNANCE_ROLES = java.util.Set.of("CONTENT_ADMIN", "SYSTEM_ADMIN");

    private final AssetRepository assetRepository;
    private final AssetCollaborationStore collaborationStore;
    private final OperationLogStore operationLogs;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public AssetWriteService(AssetRepository assetRepository, AssetCollaborationStore collaborationStore,
            OperationLogStore operationLogs) {
        this.assetRepository = assetRepository;
        this.collaborationStore = collaborationStore;
        this.operationLogs = operationLogs;
    }

    public AssetWriteService(AssetRepository assetRepository, AssetCollaborationStore collaborationStore) {
        this(assetRepository, collaborationStore, new InMemoryOperationLogStore());
    }

    public AssetWriteService(AssetRepository assetRepository) {
        this(assetRepository, new InMemoryAssetCollaborationStore(), new InMemoryOperationLogStore());
    }

    public Asset saveDraft(AssetDraft draft) {
        validateCommon(draft);
        if (assetRepository.existsByAssetNumber(draft.assetNumber())) {
            throw new DuplicateAssetNumberException(draft.assetNumber());
        }
        return assetRepository.save(new Asset(
                0,
                draft.assetNumber(),
                draft.name(),
                draft.description(),
                draft.assetType(),
                AssetStatus.DRAFT,
                draft.specialties(),
                draft.tags(),
                draft.moduleTags(),
                draft.standardEquipmentModule(),
                draft.linkedModuleAssetIds(),
                draft.equipmentInterconnectCode(),
                draft.scopes(),
                draft.files(),
                draft.ownerName(),
                draft.ownerDepartment(),
                Instant.now(),
                false));
    }

    public Asset submit(long id) {
        var asset = assetRepository.findById(id).orElseThrow(() -> new AssetNotFoundException(id));
        if (asset.assetNumber().isBlank()
                || asset.name().isBlank()
                || asset.description().isBlank()
                || asset.assetType() == null
                || asset.specialties().isEmpty()
                || asset.files().isEmpty()
                || asset.scopes().stream().noneMatch(this::isCompleteScope)) {
            throw new AssetSubmissionValidationException();
        }
        return assetRepository.update(new Asset(
                asset.id(),
                asset.assetNumber(),
                asset.name(),
                asset.description(),
                asset.assetType(),
                AssetStatus.PENDING_CURATION,
                asset.specialties(),
                asset.tags(),
                asset.moduleTags(),
                asset.standardEquipmentModule(),
                asset.linkedModuleAssetIds(),
                asset.equipmentInterconnectCode(),
                asset.scopes(),
                asset.files(),
                asset.ownerName(),
                asset.ownerDepartment(),
                Instant.now(),
                asset.legacy()));
    }

    /** 停用资料（DISABLE-01/02）：仅待整理/已标准化可停用，原因必填，停用后默认不进入普通检索。 */
    public Asset disable(long id, String reason, String operatorUserId, String operatorName, String roles) {
        if (!hasGovernanceRole(roles)) {
            throw new ForbiddenOperationException("仅内容管理员或系统管理员可以停用资料");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("停用原因不能为空");
        }
        var asset = assetRepository.findById(id).orElseThrow(() -> new AssetNotFoundException(id));
        if (asset.status() != AssetStatus.PENDING_CURATION && asset.status() != AssetStatus.STANDARDIZED) {
            throw new IllegalArgumentException("仅待整理或已标准化资料可以停用");
        }
        var disabled = assetRepository.update(new Asset(
                asset.id(),
                asset.assetNumber(),
                asset.name(),
                asset.description(),
                asset.assetType(),
                AssetStatus.DISABLED,
                asset.specialties(),
                asset.tags(),
                asset.moduleTags(),
                asset.standardEquipmentModule(),
                asset.linkedModuleAssetIds(),
                asset.equipmentInterconnectCode(),
                asset.scopes(),
                asset.files(),
                asset.ownerName(),
                asset.ownerDepartment(),
                Instant.now(),
                asset.legacy()));
        appendDisableAudit(disabled, reason, operatorUserId, operatorName, asset.status().name());
        return disabled;
    }

    private boolean hasGovernanceRole(String roles) {
        if (roles == null) return false;
        return java.util.Arrays.stream(roles.split(","))
                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                .anyMatch(GOVERNANCE_ROLES::contains);
    }

    private void appendDisableAudit(Asset asset, String reason, String operatorUserId, String operatorName,
            String fromStatus) {
        try {
            operationLogs.append(new OperationLog(0, operatorUserId, "ASSET_DISABLE", "ASSET", asset.id(),
                    objectMapper.writeValueAsString(Map.of(
                            "reason", reason,
                            "operatorName", operatorName == null ? "" : operatorName,
                            "fromStatus", fromStatus,
                            "toStatus", AssetStatus.DISABLED.name())),
                    Instant.now()));
        } catch (Exception exception) {
            throw new IllegalStateException("停用审计写入失败", exception);
        }
    }

    public boolean isFavorite(long assetId, String userId) {
        ensureAssetExists(assetId);
        return collaborationStore.isFavorite(assetId, userId);
    }

    /** 批量创建草稿（UPLOAD-05/07）：全批校验、编号批次内与全局唯一、相同文件仅提示不处理。 */
    public BatchDraftResult saveDraftsBatch(List<AssetDraft> drafts) {
        if (drafts == null || drafts.isEmpty()) {
            throw new IllegalArgumentException("请至少提供一份资产");
        }
        if (drafts.size() > 100) {
            throw new IllegalArgumentException("单批最多创建 100 份资产");
        }
        var numbers = new java.util.HashSet<String>();
        for (var draft : drafts) {
            validateCommon(draft);
            var number = draft.assetNumber().trim();
            if (number.isBlank()) throw new AssetSubmissionValidationException();
            if (!numbers.add(number.toLowerCase(java.util.Locale.ROOT))) {
                throw new DuplicateAssetNumberException(number);
            }
            if (assetRepository.existsByAssetNumber(number)) {
                throw new DuplicateAssetNumberException(number);
            }
        }
        var seenSha = new java.util.HashMap<String, String>();
        var duplicateFiles = new java.util.ArrayList<DuplicateFileInfo>();
        for (var draft : drafts) {
            if (draft.files() == null) continue;
            for (var file : draft.files()) {
                if (file.contentSha256() == null || file.contentSha256().isBlank()) continue;
                var previous = seenSha.putIfAbsent(file.contentSha256(), file.name());
                if (previous != null) duplicateFiles.add(new DuplicateFileInfo(file.name(), file.contentSha256()));
            }
        }
        var assets = drafts.stream().map(this::saveDraft).toList();
        return new BatchDraftResult(assets, List.copyOf(duplicateFiles));
    }

    public record DuplicateFileInfo(String fileName, String contentSha256) {}

    public record BatchDraftResult(List<Asset> assets, List<DuplicateFileInfo> duplicateFiles) {}

    public boolean setFavorite(long assetId, String userId, boolean favorite) {
        ensureAssetExists(assetId);
        return collaborationStore.setFavorite(assetId, userId, favorite);
    }

    public synchronized List<Long> favoriteAssetIds(String userId) {
        return collaborationStore.favoriteAssetIds(userId);
    }

    public synchronized List<CommentView> comments(long assetId, String userId) {
        return comments(assetId, userId, false);
    }

    public synchronized List<CommentView> comments(long assetId, String userId, boolean canModerate) {
        ensureAssetExists(assetId);
        var normalizedUser = normalizeUser(userId);
        return collaborationStore.comments(assetId, userId).stream()
                .map(stored -> toCommentView(stored.comment(), normalizedUser, canModerate, stored.likedByCurrentUser()))
                .toList();
    }

    public synchronized CommentView comment(long assetId, long commentId, String userId) {
        return comment(assetId, commentId, userId, false);
    }

    public synchronized CommentView comment(long assetId, long commentId, String userId, boolean canModerate) {
        ensureAssetExists(assetId);
        var stored = collaborationStore.comments(assetId, userId).stream()
                .filter(item -> item.comment().id() == commentId)
                .findFirst()
                .orElseThrow(() -> new AssetNotFoundException(commentId));
        return toCommentView(stored.comment(), normalizeUser(userId), canModerate, stored.likedByCurrentUser());
    }

    public synchronized AssetComment addComment(long assetId, String userId, String authorName, String content) {
        return addComment(assetId, userId, authorName, content, List.of());
    }

    public synchronized AssetComment addComment(
            long assetId, String userId, String authorName, String content, List<String> imageKeys) {
        ensureAssetExists(assetId);
        var normalizedImages = imageKeys == null ? List.<String>of() : imageKeys.stream()
                .filter(key -> key != null && !key.isBlank())
                .distinct()
                .toList();
        if ((content == null || content.isBlank()) && normalizedImages.isEmpty()) {
            throw new CommentValidationException("评论内容和图片不能同时为空");
        }
        if (content != null && content.trim().length() > 500) {
            throw new CommentValidationException("评论内容不能超过 500 个字符");
        }
        if (normalizedImages.size() > 6) {
            throw new CommentValidationException("评论图片不能超过 6 张");
        }
        var normalizedUser = normalizeUser(userId);
        return collaborationStore.addComment(assetId, normalizedUser,
                authorName == null || authorName.isBlank() ? normalizedUser : authorName,
                content == null ? "" : content.trim(), normalizedImages);
    }

    public synchronized void deleteComment(long assetId, long commentId, String userId) {
        deleteComment(assetId, commentId, userId, false);
    }

    public synchronized void deleteComment(long assetId, long commentId, String userId, boolean canModerate) {
        var comment = comment(assetId, commentId, userId, canModerate).comment();
        if (!canModerate && !comment.authorId().equals(normalizeUser(userId))) {
            throw new ForbiddenOperationException("只能删除自己的评论");
        }
        collaborationStore.deleteComment(assetId, commentId);
    }

    public synchronized CommentLikeResponseState setCommentLike(
            long assetId, long commentId, String userId, boolean liked) {
        var comment = comment(assetId, commentId, userId, false).comment();
        if (comment.deleted()) {
            throw new ForbiddenOperationException("已删除的评论不能点赞");
        }
        var result = collaborationStore.setCommentLike(assetId, commentId, userId, liked);
        return new CommentLikeResponseState(result.liked(), result.likeCount());
    }

    public synchronized boolean isCommentImageLinked(long assetId, String storageKey) {
        ensureAssetExists(assetId);
        return collaborationStore.isCommentImageLinked(assetId, storageKey);
    }

    private CommentView toCommentView(
            AssetComment comment, String normalizedUser, boolean canModerate, boolean likedByCurrentUser) {
        return new CommentView(
                comment,
                likedByCurrentUser,
                canModerate || comment.authorId().equals(normalizedUser));
    }

    private void ensureAssetExists(long assetId) {
        if (assetRepository.findById(assetId).isEmpty()) {
            throw new AssetNotFoundException(assetId);
        }
    }

    private String normalizeUser(String userId) {
        return userId == null || userId.isBlank() ? "demo-user" : userId;
    }

    private void validateCommon(AssetDraft draft) {
        if (draft.assetNumber() == null || draft.name() == null || draft.description() == null) {
            throw new AssetSubmissionValidationException();
        }
        if (draft.files() == null) {
            throw new AssetSubmissionValidationException();
        }
    }

    private boolean isCompleteScope(AssetScope scope) {
        return scope != null
                && !scope.platform().isBlank()
                && !scope.productLine().isBlank()
                && !scope.base().isBlank()
                && !scope.productionLine().isBlank();
    }

    public record AssetDraft(
            String assetNumber,
            String name,
            String description,
            com.tianshu.assets.asset.domain.AssetType assetType,
            List<String> specialties,
            List<String> tags,
            List<String> moduleTags,
            boolean standardEquipmentModule,
            List<Long> linkedModuleAssetIds,
            String equipmentInterconnectCode,
            List<AssetScope> scopes,
            List<AssetFile> files,
            String ownerName,
            String ownerDepartment) {

        public AssetDraft {
            specialties = specialties == null ? List.of() : List.copyOf(specialties);
            tags = tags == null ? List.of() : List.copyOf(tags);
            moduleTags = moduleTags == null ? List.of() : List.copyOf(moduleTags);
            linkedModuleAssetIds = linkedModuleAssetIds == null ? List.of() : List.copyOf(linkedModuleAssetIds);
            equipmentInterconnectCode = equipmentInterconnectCode == null ? "" : equipmentInterconnectCode;
            scopes = scopes == null ? List.of() : List.copyOf(scopes);
            files = files == null ? List.of() : List.copyOf(files);
        }

        public AssetDraft(String assetNumber, String name, String description,
                com.tianshu.assets.asset.domain.AssetType assetType, List<String> specialties,
                List<String> tags, List<AssetScope> scopes, List<AssetFile> files,
                String ownerName, String ownerDepartment) {
            this(assetNumber, name, description, assetType, specialties, tags, List.of(), false,
                    List.of(), "", scopes, files, ownerName, ownerDepartment);
        }
    }

    public record CommentLikeResponseState(boolean liked, long likeCount) {}

    public record CommentView(AssetComment comment, boolean likedByCurrentUser, boolean canDelete) {}
}
