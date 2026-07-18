package com.tianshu.assets.common.api;

import com.tianshu.assets.asset.application.AssetNotFoundException;
import com.tianshu.assets.asset.application.AssetSubmissionValidationException;
import com.tianshu.assets.asset.application.DuplicateAssetNumberException;
import com.tianshu.assets.asset.application.CommentValidationException;
import com.tianshu.assets.asset.application.ForbiddenOperationException;
import com.tianshu.assets.asset.application.AssetFileValidationException;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(AssetNotFoundException.class)
    ResponseEntity<ApiError> handleNotFound(AssetNotFoundException exception) {
        return response(HttpStatus.NOT_FOUND, "asset_not_found", exception.getMessage(), List.of());
    }

    @ExceptionHandler(DuplicateAssetNumberException.class)
    ResponseEntity<ApiError> handleDuplicate(DuplicateAssetNumberException exception) {
        return response(HttpStatus.CONFLICT, "duplicate_asset_number", exception.getMessage(), List.of());
    }

    @ExceptionHandler(AssetSubmissionValidationException.class)
    ResponseEntity<ApiError> handleSubmissionValidation(AssetSubmissionValidationException exception) {
        return response(HttpStatus.UNPROCESSABLE_ENTITY, "asset_submission_invalid", exception.getMessage(), List.of());
    }

    @ExceptionHandler(CommentValidationException.class)
    ResponseEntity<ApiError> handleCommentValidation(CommentValidationException exception) {
        return response(HttpStatus.UNPROCESSABLE_ENTITY, "comment_invalid", exception.getMessage(), List.of());
    }

    @ExceptionHandler(AssetFileValidationException.class)
    ResponseEntity<ApiError> handleFileValidation(AssetFileValidationException exception) {
        return response(HttpStatus.UNPROCESSABLE_ENTITY, "file_invalid", exception.getMessage(), List.of());
    }

    @ExceptionHandler(ForbiddenOperationException.class)
    ResponseEntity<ApiError> handleForbidden(ForbiddenOperationException exception) {
        return response(HttpStatus.FORBIDDEN, "operation_forbidden", exception.getMessage(), List.of());
    }

    @ExceptionHandler(UnsupportedOperationException.class)
    ResponseEntity<ApiError> handleReadOnly(UnsupportedOperationException exception) {
        return response(HttpStatus.CONFLICT, "read_only_adapter", exception.getMessage(), List.of());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException exception) {
        return response(HttpStatus.UNPROCESSABLE_ENTITY, "invalid_request", exception.getMessage(), List.of());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiError> handleMaxUpload(MaxUploadSizeExceededException exception) {
        return response(HttpStatus.PAYLOAD_TOO_LARGE, "file_too_large", "单个文件或上传批次超过大小限制", List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception) {
        var details = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiError.FieldError(
                        error.getField(), error.getDefaultMessage(), "invalid_value"))
                .toList();
        return response(HttpStatus.BAD_REQUEST, "validation_error", "请求参数校验失败", details);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException exception) {
        var details = exception.getConstraintViolations().stream()
                .map(violation -> new ApiError.FieldError(
                        violation.getPropertyPath().toString(),
                        violation.getMessage(),
                        "invalid_value"))
                .toList();
        return response(HttpStatus.BAD_REQUEST, "validation_error", "请求参数校验失败", details);
    }

    private ResponseEntity<ApiError> response(
            HttpStatus status, String code, String message, List<ApiError.FieldError> details) {
        return ResponseEntity.status(status)
                .body(new ApiError(new ApiError.ErrorBody(code, message, details)));
    }
}
