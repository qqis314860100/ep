package com.tianshu.assets.asset.api;

import com.tianshu.assets.asset.application.AssetQueryService;
import com.tianshu.assets.asset.application.AssetWriteService;
import com.tianshu.assets.asset.domain.AssetFile;
import com.tianshu.assets.asset.domain.AssetScope;
import com.tianshu.assets.asset.domain.AssetSearchCriteria;
import com.tianshu.assets.asset.domain.AssetStatus;
import com.tianshu.assets.common.file.FileStorage;
import com.tianshu.assets.common.file.InMemoryFileStorage;
import com.tianshu.assets.asset.domain.AssetType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.web.multipart.MultipartFile;

@Validated
@RestController
@RequestMapping("/api/v1/assets")
public class AssetController {

    private static final long MAX_COMMENT_IMAGE_SIZE = 10L * 1024 * 1024;
    private static final int MAX_COMMENT_IMAGES = 6;
    private static final Set<String> COMMENT_IMAGE_FORMATS = Set.of("PNG", "JPG", "JPEG", "WEBP");

    private final AssetQueryService assetQueryService;
    private final AssetWriteService assetWriteService;
    private final FileStorage assetFileStorage;

    @Autowired
    public AssetController(AssetQueryService assetQueryService, AssetWriteService assetWriteService, FileStorage assetFileStorage) {
        this.assetQueryService = assetQueryService;
        this.assetWriteService = assetWriteService;
        this.assetFileStorage = assetFileStorage;
    }

    public AssetController(AssetQueryService assetQueryService, AssetWriteService assetWriteService) {
        this(assetQueryService, assetWriteService, new InMemoryFileStorage());
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
            @RequestParam(required = false) Boolean previewable,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(name = "per_page", defaultValue = "20") @Min(1) @Max(100) int perPage) {
        var result = assetQueryService.search(
                new AssetSearchCriteria(q, assetType, status, "", platformFamily, platformVariant, base, productionLine, previewable, page, perPage));
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
    public List<CommentResponse> comments(
            @PathVariable @Min(1) long id,
            @RequestHeader(name = "X-User-Id", defaultValue = "demo-user") String userId,
            @RequestHeader(name = "X-User-Roles", defaultValue = "") String roles) {
        return assetWriteService.comments(id, userId, canModerateComments(roles)).stream().map(CommentResponse::from).toList();
    }

    @PostMapping(value = "/{id}/comments", consumes = MediaType.APPLICATION_JSON_VALUE)
    public CommentResponse addComment(
            @PathVariable @Min(1) long id,
            @RequestHeader(name = "X-User-Id", defaultValue = "demo-user") String userId,
            @RequestBody CommentRequest request) {
        var comment = assetWriteService.addComment(id, userId, request.authorName(), request.content(), request.imageKeys());
        return CommentResponse.from(assetWriteService.comment(id, comment.id(), userId));
    }

    @PostMapping(value = "/{id}/comments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CommentResponse addCommentWithImages(
            @PathVariable @Min(1) long id,
            @RequestHeader(name = "X-User-Id", defaultValue = "demo-user") String userId,
            @RequestPart(name = "authorName", required = false) String authorName,
            @RequestPart(name = "content", required = false) String content,
            @RequestPart(name = "images", required = false) List<MultipartFile> images) throws IOException {
        assetQueryService.get(id);
        var imageKeys = storeCommentImages(images);
        var comment = assetWriteService.addComment(id, userId, authorName, content, imageKeys);
        return CommentResponse.from(assetWriteService.comment(id, comment.id(), userId));
    }

    @GetMapping("/{assetId}/comments/images/{storageKey}")
    public ResponseEntity<ByteArrayResource> commentImage(
            @PathVariable @Min(1) long assetId,
            @PathVariable String storageKey) {
        if (!assetWriteService.isCommentImageLinked(assetId, storageKey)) {
            throw new com.tianshu.assets.asset.application.AssetNotFoundException(assetId);
        }
        var stored = assetFileStorage.open(storageKey)
                .orElseThrow(() -> new com.tianshu.assets.asset.application.AssetNotFoundException(assetId));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(stored.contentType()))
                .contentLength(stored.size())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300")
                .body(new ByteArrayResource(stored.content()));
    }

    @DeleteMapping("/{assetId}/comments/{commentId}")
    public void deleteComment(
            @PathVariable @Min(1) long assetId,
            @PathVariable @Min(1) long commentId,
            @RequestHeader(name = "X-User-Id", defaultValue = "demo-user") String userId,
            @RequestHeader(name = "X-User-Roles", defaultValue = "") String roles) {
        assetWriteService.deleteComment(assetId, commentId, userId, canModerateComments(roles));
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

    private List<String> storeCommentImages(List<MultipartFile> images) throws IOException {
        if (images == null || images.isEmpty()) return List.of();
        if (images.size() > MAX_COMMENT_IMAGES) {
            throw new com.tianshu.assets.asset.application.CommentValidationException("评论图片不能超过 6 张");
        }
        var validated = new java.util.ArrayList<ValidatedCommentImage>();
        for (var image : images) {
            if (image.isEmpty() || image.getOriginalFilename() == null) {
                throw new com.tianshu.assets.asset.application.CommentValidationException("评论图片不能为空");
            }
            if (image.getSize() > MAX_COMMENT_IMAGE_SIZE) {
                throw new com.tianshu.assets.asset.application.CommentValidationException("单张评论图片不能超过 10 MB");
            }
            var filename = image.getOriginalFilename();
            var format = fileFormat(filename);
            var bytes = image.getBytes();
            validateCommentImage(format, bytes);
            var contentType = image.getContentType() == null || image.getContentType().isBlank()
                    ? "application/octet-stream"
                    : image.getContentType();
            validated.add(new ValidatedCommentImage(filename, contentType, bytes));
        }
        var keys = new java.util.ArrayList<String>();
        for (var image : validated) {
            keys.add(assetFileStorage.store(
                    new ByteArrayInputStream(image.content()),
                    image.content().length,
                    image.filename(),
                    image.contentType()));
        }
        return List.copyOf(keys);
    }

    private void validateCommentImage(String format, byte[] bytes) {
        if (!COMMENT_IMAGE_FORMATS.contains(format)) {
            throw new com.tianshu.assets.asset.application.CommentValidationException("评论图片仅支持 PNG、JPG、JPEG 和 WEBP");
        }
        var png = format.equals("PNG") && bytes.length >= 8
                && bytes[0] == (byte) 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4e && bytes[3] == 0x47;
        var jpeg = (format.equals("JPG") || format.equals("JPEG")) && bytes.length >= 3
                && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff;
        var webp = format.equals("WEBP") && bytes.length >= 12
                && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
        if (!png && !jpeg && !webp) {
            throw new com.tianshu.assets.asset.application.CommentValidationException("评论图片扩展名与实际内容不一致");
        }
    }

    private String fileFormat(String filename) {
        var dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toUpperCase(Locale.ROOT);
    }

    private boolean canModerateComments(String roles) {
        if (roles == null || roles.isBlank()) return false;
        return java.util.Arrays.stream(roles.split(","))
                .map(String::trim)
                .anyMatch(role -> role.equals("CONTENT_ADMIN") || role.equals("SYSTEM_ADMIN"));
    }

    private record ValidatedCommentImage(String filename, String contentType, byte[] content) {}

    public record CommentRequest(String authorName, String content, List<String> imageKeys) {}
}
