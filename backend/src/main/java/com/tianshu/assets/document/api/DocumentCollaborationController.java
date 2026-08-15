package com.tianshu.assets.document.api;

import com.tianshu.assets.document.application.DocumentCollaborationService;
import com.tianshu.assets.document.application.DocumentCollaborationStore;
import com.tianshu.assets.document.domain.DocumentComment;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 文档协作（收藏/评论带版本上下文/点赞）与我的文档入口。 */
@RestController
@RequestMapping("/api/v1/documents")
public class DocumentCollaborationController {

    private final DocumentCollaborationService service;

    @Autowired
    public DocumentCollaborationController(DocumentCollaborationService service) {
        this.service = service;
    }

    @GetMapping("/{documentId}/favorite")
    public FavoriteResponse favorite(
            @PathVariable @Min(1) long documentId,
            @RequestHeader(name = "X-User-Id", defaultValue = "demo-user") String userId) {
        return new FavoriteResponse(documentId, service.isFavorite(documentId, userId));
    }

    @PostMapping("/{documentId}/favorite")
    public FavoriteResponse addFavorite(
            @PathVariable @Min(1) long documentId,
            @RequestHeader(name = "X-User-Id", defaultValue = "demo-user") String userId) {
        return new FavoriteResponse(documentId, service.setFavorite(documentId, userId, true));
    }

    @DeleteMapping("/{documentId}/favorite")
    public FavoriteResponse removeFavorite(
            @PathVariable @Min(1) long documentId,
            @RequestHeader(name = "X-User-Id", defaultValue = "demo-user") String userId) {
        return new FavoriteResponse(documentId, service.setFavorite(documentId, userId, false));
    }

    @GetMapping("/my/favorites")
    public List<DocumentResponse> myFavorites(
            @RequestHeader(name = "X-User-Id", defaultValue = "demo-user") String userId) {
        return service.myFavorites(userId).stream().map(DocumentResponse::from).toList();
    }

    @GetMapping("/mine")
    public List<DocumentResponse> mine(
            @RequestHeader(name = "X-User-Id", defaultValue = "demo-user") String maintainerId,
            @RequestParam(required = false) String status) {
        return service.myDocuments(maintainerId, status).stream().map(DocumentResponse::from).toList();
    }

    @GetMapping("/{documentId}/comments")
    public List<CommentResponse> comments(
            @PathVariable @Min(1) long documentId,
            @RequestHeader(name = "X-User-Id", defaultValue = "demo-user") String userId) {
        return service.comments(documentId, userId).stream()
                .map(stored -> CommentResponse.from(stored.comment(), stored.likedByCurrentUser()))
                .toList();
    }

    @PostMapping("/{documentId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentResponse addComment(
            @PathVariable @Min(1) long documentId,
            @RequestHeader(name = "X-User-Id", defaultValue = "demo-user") String userId,
            @RequestHeader(name = "X-User-Name", defaultValue = "当前用户") String userName,
            @RequestBody AddCommentRequest request) {
        return CommentResponse.from(service.addComment(documentId, request.versionId(), userId, userName,
                request.content(), request.imageKeys() == null ? List.of() : request.imageKeys()), false);
    }

    @PostMapping("/{documentId}/comments/{commentId}/like")
    public LikeResponse like(
            @PathVariable @Min(1) long documentId,
            @PathVariable @Min(1) long commentId,
            @RequestHeader(name = "X-User-Id", defaultValue = "demo-user") String userId) {
        var state = service.setCommentLike(documentId, commentId, userId, true);
        return new LikeResponse(state.liked(), state.likeCount());
    }

    @DeleteMapping("/{documentId}/comments/{commentId}/like")
    public LikeResponse unlike(
            @PathVariable @Min(1) long documentId,
            @PathVariable @Min(1) long commentId,
            @RequestHeader(name = "X-User-Id", defaultValue = "demo-user") String userId) {
        var state = service.setCommentLike(documentId, commentId, userId, false);
        return new LikeResponse(state.liked(), state.likeCount());
    }

    @DeleteMapping("/{documentId}/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteComment(
            @PathVariable @Min(1) long documentId,
            @PathVariable @Min(1) long commentId,
            @RequestHeader(name = "X-User-Id", defaultValue = "demo-user") String userId,
            @RequestHeader(name = "X-User-Roles", defaultValue = "") String roles) {
        service.deleteComment(documentId, commentId, userId, roles);
    }

    public record FavoriteResponse(long documentId, boolean favorite) {}

    public record AddCommentRequest(
            @NotNull(message = "评论版本不能为空") @Min(1) Long versionId,
            @NotBlank(message = "评论内容不能为空") String content,
            @Size(max = 6, message = "评论图片最多 6 张") List<String> imageKeys) {}

    public record CommentResponse(
            long id,
            long documentId,
            long versionId,
            String authorId,
            String authorName,
            String content,
            List<String> imageKeys,
            long likeCount,
            boolean deleted,
            boolean likedByCurrentUser,
            String createdAt) {

        static CommentResponse from(DocumentComment comment, boolean likedByCurrentUser) {
            return new CommentResponse(comment.id(), comment.documentId(), comment.versionId(),
                    comment.authorId(), comment.authorName(), comment.content(), comment.imageKeys(),
                    comment.likeCount(), comment.deleted(), likedByCurrentUser, comment.createdAt());
        }
    }

    public record LikeResponse(boolean liked, long likeCount) {}
}
