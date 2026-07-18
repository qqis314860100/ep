package com.tianshu.assets.asset.api;

import com.tianshu.assets.asset.application.AssetQueryService;
import com.tianshu.assets.asset.application.AssetWriteService;
import com.tianshu.assets.asset.application.AssetFileStorage;
import com.tianshu.assets.asset.infrastructure.InMemoryAssetFileStorage;
import com.tianshu.assets.asset.domain.AssetFile;
import com.tianshu.assets.asset.domain.AssetScope;
import com.tianshu.assets.asset.domain.AssetSearchCriteria;
import com.tianshu.assets.asset.domain.AssetStatus;
import com.tianshu.assets.asset.domain.AssetType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.ByteArrayResource;

@Validated
@RestController
@RequestMapping("/api/v1/assets")
public class AssetController {

    private final AssetQueryService assetQueryService;
    private final AssetWriteService assetWriteService;
    private final AssetFileStorage assetFileStorage;

    @Autowired
    public AssetController(AssetQueryService assetQueryService, AssetWriteService assetWriteService, AssetFileStorage assetFileStorage) {
        this.assetQueryService = assetQueryService;
        this.assetWriteService = assetWriteService;
        this.assetFileStorage = assetFileStorage;
    }

    public AssetController(AssetQueryService assetQueryService, AssetWriteService assetWriteService) {
        this(assetQueryService, assetWriteService, new InMemoryAssetFileStorage());
    }

    @GetMapping
    public PageResponse<AssetResponse> search(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(name = "asset_type", required = false) AssetType assetType,
            @RequestParam(required = false) AssetStatus status,
            @RequestParam(name = "platform_family", required = false) String platformFamily,
            @RequestParam(name = "platform_variant", required = false) String platformVariant,
            @RequestParam(required = false) String base,
            @RequestParam(name = "production_line", required = false) String productionLine,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(name = "per_page", defaultValue = "20") @Min(1) @Max(100) int perPage) {
        var result = assetQueryService.search(
                new AssetSearchCriteria(q, assetType, status, "", platformFamily, platformVariant, base, productionLine, page, perPage));
        var data = result.items().stream().map(AssetResponse::from).toList();
        return new PageResponse<>(data, PageResponse.Meta.of(result.total(), result.page(), result.perPage()));
    }

    @GetMapping("/{id}")
    public AssetResponse get(@PathVariable @Min(1) long id) {
        return AssetResponse.from(assetQueryService.get(id));
    }

    @GetMapping("/{id}/relations")
    public List<AssetRelationResponse> relations(@PathVariable @Min(1) long id) {
        return assetQueryService.getRelations(id).stream()
                .map(AssetRelationResponse::from)
                .toList();
    }

    @GetMapping("/{assetId}/files/{fileId}")
    public ResponseEntity<ByteArrayResource> file(
            @PathVariable @Min(1) long assetId,
            @PathVariable @Min(1) long fileId,
            @RequestParam(defaultValue = "false") boolean preview) {
        var asset = assetQueryService.get(assetId);
        var file = asset.files().stream().filter(item -> item.id() == fileId).findFirst()
                .orElseThrow(() -> new com.tianshu.assets.asset.application.AssetNotFoundException(fileId));
        if (file.storageKey().isBlank()) {
            throw new com.tianshu.assets.asset.application.AssetNotFoundException(fileId);
        }
        var stored = assetFileStorage.open(file.storageKey())
                .orElseThrow(() -> new com.tianshu.assets.asset.application.AssetNotFoundException(fileId));
        var disposition = preview && file.previewable() ? "inline" : "attachment";
        var contentType = MediaType.parseMediaType(stored.contentType());
        return ResponseEntity.ok()
                .contentType(contentType)
                .contentLength(stored.size())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition + "; filename=\"" + file.name().replace("\"", "") + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new ByteArrayResource(stored.content()));
    }

    @PostMapping("/drafts")
    @ResponseStatus(HttpStatus.CREATED)
    public AssetResponse saveDraft(@RequestBody AssetWriteRequest request) {
        return AssetResponse.from(assetWriteService.saveDraft(request.toDraft()));
    }

    @PostMapping("/{id}/submit")
    public AssetResponse submit(@PathVariable @Min(1) long id) {
        return AssetResponse.from(assetWriteService.submit(id));
    }

    @GetMapping("/{id}/favorite")
    public FavoriteResponse favorite(
            @PathVariable @Min(1) long id,
            @RequestHeader(name = "X-User-Id", defaultValue = "demo-user") String userId) {
        return new FavoriteResponse(id, assetWriteService.isFavorite(id, userId));
    }

    @PostMapping("/{id}/favorite")
    public FavoriteResponse addFavorite(
            @PathVariable @Min(1) long id,
            @RequestHeader(name = "X-User-Id", defaultValue = "demo-user") String userId) {
        return new FavoriteResponse(id, assetWriteService.setFavorite(id, userId, true));
    }

    @DeleteMapping("/{id}/favorite")
    public FavoriteResponse removeFavorite(
            @PathVariable @Min(1) long id,
            @RequestHeader(name = "X-User-Id", defaultValue = "demo-user") String userId) {
        return new FavoriteResponse(id, assetWriteService.setFavorite(id, userId, false));
    }

    @GetMapping("/{id}/comments")
    public List<CommentResponse> comments(@PathVariable @Min(1) long id) {
        return assetWriteService.comments(id).stream().map(CommentResponse::from).toList();
    }

    @PostMapping("/{id}/comments")
    public CommentResponse addComment(
            @PathVariable @Min(1) long id,
            @RequestHeader(name = "X-User-Id", defaultValue = "demo-user") String userId,
            @RequestBody CommentRequest request) {
        return CommentResponse.from(assetWriteService.addComment(id, userId, request.authorName(), request.content()));
    }

    @DeleteMapping("/{assetId}/comments/{commentId}")
    public void deleteComment(
            @PathVariable @Min(1) long assetId,
            @PathVariable @Min(1) long commentId,
            @RequestHeader(name = "X-User-Id", defaultValue = "demo-user") String userId) {
        assetWriteService.deleteComment(assetId, commentId, userId);
    }

    @PostMapping("/{assetId}/comments/{commentId}/like")
    public CommentLikeResponse likeComment(
            @PathVariable @Min(1) long assetId,
            @PathVariable @Min(1) long commentId,
            @RequestHeader(name = "X-User-Id", defaultValue = "demo-user") String userId) {
        var result = assetWriteService.setCommentLike(assetId, commentId, userId, true);
        return new CommentLikeResponse(commentId, result.liked(), result.likeCount());
    }

    @DeleteMapping("/{assetId}/comments/{commentId}/like")
    public CommentLikeResponse unlikeComment(
            @PathVariable @Min(1) long assetId,
            @PathVariable @Min(1) long commentId,
            @RequestHeader(name = "X-User-Id", defaultValue = "demo-user") String userId) {
        var result = assetWriteService.setCommentLike(assetId, commentId, userId, false);
        return new CommentLikeResponse(commentId, result.liked(), result.likeCount());
    }

    public record AssetWriteRequest(
            String assetNumber,
            String name,
            String description,
            AssetType assetType,
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

        public AssetWriteService.AssetDraft toDraft() {
            return new AssetWriteService.AssetDraft(
                    assetNumber,
                    name,
                    description,
                    assetType,
                    specialties,
                    tags,
                    moduleTags,
                    standardEquipmentModule,
                    linkedModuleAssetIds,
                    equipmentInterconnectCode,
                    scopes,
                    files,
                    ownerName,
                    ownerDepartment);
        }
    }

    public record CommentRequest(String authorName, String content) {}
}
