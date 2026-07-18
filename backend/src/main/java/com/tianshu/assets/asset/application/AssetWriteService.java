package com.tianshu.assets.asset.application;

import com.tianshu.assets.asset.domain.Asset;
import com.tianshu.assets.asset.domain.AssetComment;
import com.tianshu.assets.asset.domain.AssetFile;
import com.tianshu.assets.asset.domain.AssetRepository;
import com.tianshu.assets.asset.domain.AssetScope;
import com.tianshu.assets.asset.domain.AssetStatus;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;

@Service
public class AssetWriteService {

    private final AssetRepository assetRepository;
    private final Set<String> favorites = ConcurrentHashMap.newKeySet();
    private final List<AssetComment> comments = new ArrayList<>();
    private final Map<String, Set<Long>> commentLikes = new ConcurrentHashMap<>();
    private final AtomicLong nextCommentId = new AtomicLong(1);

    public AssetWriteService(AssetRepository assetRepository) {
        this.assetRepository = assetRepository;
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

    public boolean isFavorite(long assetId, String userId) {
        ensureAssetExists(assetId);
        return favorites.contains(favoriteKey(assetId, userId));
    }

    public boolean setFavorite(long assetId, String userId, boolean favorite) {
        ensureAssetExists(assetId);
        var key = favoriteKey(assetId, userId);
        if (favorite) {
            favorites.add(key);
        } else {
            favorites.remove(key);
        }
        return favorite;
    }

    public synchronized List<Long> favoriteAssetIds(String userId) {
        var normalizedUser = userId == null || userId.isBlank() ? "demo-user" : userId;
        var prefix = normalizedUser + ":";
        return favorites.stream()
                .filter(key -> key.startsWith(prefix))
                .map(key -> key.substring(prefix.length()))
                .map(Long::parseLong)
                .sorted()
                .toList();
    }

    public synchronized List<AssetComment> comments(long assetId) {
        ensureAssetExists(assetId);
        return comments.stream().filter(comment -> comment.assetId() == assetId).toList();
    }

    public synchronized AssetComment addComment(long assetId, String userId, String authorName, String content) {
        ensureAssetExists(assetId);
        if (content == null || content.isBlank()) {
            throw new CommentValidationException();
        }
        var normalizedUser = userId == null || userId.isBlank() ? "demo-user" : userId;
        var comment = new AssetComment(
                nextCommentId.getAndIncrement(),
                assetId,
                normalizedUser,
                authorName == null || authorName.isBlank() ? normalizedUser : authorName,
                content.trim(),
                Instant.now(),
                false,
                0);
        comments.add(comment);
        return comment;
    }

    public synchronized void deleteComment(long assetId, long commentId, String userId) {
        var comment = findComment(assetId, commentId);
        if (!comment.authorId().equals(userId == null || userId.isBlank() ? "demo-user" : userId)) {
            throw new ForbiddenOperationException("只能删除自己的评论");
        }
        comments.set(comments.indexOf(comment), new AssetComment(
                comment.id(), comment.assetId(), comment.authorId(), comment.authorName(), comment.content(),
                comment.createdAt(), true, comment.likeCount()));
    }

    public synchronized CommentLikeResponseState setCommentLike(
            long assetId, long commentId, String userId, boolean liked) {
        var comment = findComment(assetId, commentId);
        var normalizedUser = userId == null || userId.isBlank() ? "demo-user" : userId;
        var userLikes = commentLikes.computeIfAbsent(normalizedUser, ignored -> ConcurrentHashMap.newKeySet());
        if (liked) userLikes.add(commentId);
        else userLikes.remove(commentId);
        var count = commentLikes.values().stream().filter(likes -> likes.contains(commentId)).count();
        var updated = new AssetComment(
                comment.id(), comment.assetId(), comment.authorId(), comment.authorName(), comment.content(),
                comment.createdAt(), comment.deleted(), count);
        comments.set(comments.indexOf(comment), updated);
        return new CommentLikeResponseState(liked, count);
    }

    private AssetComment findComment(long assetId, long commentId) {
        return comments.stream()
                .filter(comment -> comment.assetId() == assetId && comment.id() == commentId)
                .findFirst()
                .orElseThrow(() -> new AssetNotFoundException(commentId));
    }

    private void ensureAssetExists(long assetId) {
        if (assetRepository.findById(assetId).isEmpty()) {
            throw new AssetNotFoundException(assetId);
        }
    }

    private String favoriteKey(long assetId, String userId) {
        return (userId == null || userId.isBlank() ? "demo-user" : userId) + ":" + assetId;
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
}
