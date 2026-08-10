package com.tianshu.assets.common.api;

import com.tianshu.assets.asset.application.AssetNotFoundException;
import com.tianshu.assets.asset.application.AssetSubmissionValidationException;
import com.tianshu.assets.asset.application.DuplicateAssetNumberException;
import com.tianshu.assets.asset.application.CommentValidationException;
import com.tianshu.assets.asset.application.ForbiddenOperationException;
import com.tianshu.assets.asset.application.AssetFileValidationException;
import com.tianshu.assets.governance.application.GovernanceTaskStateException;
import com.tianshu.assets.governance.application.GovernanceConflictException;
import com.tianshu.assets.governance.application.GovernanceVersionConflictException;
import com.tianshu.assets.governance.application.GovernanceValidationException;
import com.tianshu.assets.governance.application.GovernanceAuthorizationException;
import com.tianshu.assets.governance.application.GovernanceNotFoundException;
import com.tianshu.assets.dictionary.application.DictionaryConflictException;
import com.tianshu.assets.dictionary.application.DictionaryNotFoundException;
import com.tianshu.assets.document.application.DocumentNotFoundException;
import com.tianshu.assets.document.application.DocumentPublishValidationException;
import com.tianshu.assets.document.application.DocumentStateConflictException;
import com.tianshu.assets.document.application.DuplicateDocumentNumberException;
import com.tianshu.assets.documentrelation.application.AssetDocumentRelationConflictException;
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

    @ExceptionHandler(GovernanceTaskStateException.class)
    ResponseEntity<ApiError> handleGovernanceStateConflict(GovernanceTaskStateException exception) {
        return response(HttpStatus.CONFLICT, "governance_state_conflict", exception.getMessage(), List.of());
    }

    @ExceptionHandler(GovernanceConflictException.class)
    ResponseEntity<ApiError> handleGovernanceConflict(GovernanceConflictException exception) {
        return response(HttpStatus.CONFLICT, "governance_state_conflict", exception.getMessage(), List.of());
    }

    @ExceptionHandler(GovernanceNotFoundException.class)
    ResponseEntity<ApiError> handleGovernanceNotFound(GovernanceNotFoundException exception) {
        return response(HttpStatus.NOT_FOUND, "governance_not_found", exception.getMessage(), List.of());
    }

    @ExceptionHandler(GovernanceAuthorizationException.class)
    ResponseEntity<ApiError> handleGovernanceForbidden(GovernanceAuthorizationException exception) {
        return response(HttpStatus.FORBIDDEN, "governance_forbidden", exception.getMessage(), List.of());
    }

    @ExceptionHandler(GovernanceVersionConflictException.class)
    ResponseEntity<ApiError> handleGovernanceVersionConflict(GovernanceVersionConflictException exception) {
        return response(HttpStatus.CONFLICT, "governance_version_conflict", exception.getMessage(), List.of());
    }

    @ExceptionHandler(GovernanceValidationException.class)
    ResponseEntity<ApiError> handleGovernanceValidation(GovernanceValidationException exception) {
        var details = exception.validationMessages().stream()
                .map(message -> new ApiError.FieldError("governance", message, "invalid_state"))
                .toList();
        return response(HttpStatus.UNPROCESSABLE_ENTITY,
                "governance_validation_failed", exception.getMessage(), details);
    }

    @ExceptionHandler(DictionaryNotFoundException.class)
    ResponseEntity<ApiError> handleDictionaryNotFound(DictionaryNotFoundException exception) {
        return response(HttpStatus.NOT_FOUND, "dictionary_item_not_found", exception.getMessage(), List.of());
    }

    @ExceptionHandler(DictionaryConflictException.class)
    ResponseEntity<ApiError> handleDictionaryConflict(DictionaryConflictException exception) {
        return response(HttpStatus.CONFLICT, "dictionary_item_conflict", exception.getMessage(), List.of());
    }

    @ExceptionHandler(DocumentNotFoundException.class)
    ResponseEntity<ApiError> handleDocumentNotFound(DocumentNotFoundException exception) {
        return response(HttpStatus.NOT_FOUND, "document_not_found", exception.getMessage(), List.of());
    }

    @ExceptionHandler(DuplicateDocumentNumberException.class)
    ResponseEntity<ApiError> handleDuplicateDocumentNumber(DuplicateDocumentNumberException exception) {
        return response(HttpStatus.CONFLICT, "duplicate_document_number", exception.getMessage(), List.of());
    }

    @ExceptionHandler(DocumentStateConflictException.class)
    ResponseEntity<ApiError> handleDocumentStateConflict(DocumentStateConflictException exception) {
        return response(HttpStatus.CONFLICT, "document_state_conflict", exception.getMessage(), List.of());
    }

    @ExceptionHandler(DocumentPublishValidationException.class)
    ResponseEntity<ApiError> handleDocumentPublishValidation(DocumentPublishValidationException exception) {
        return response(HttpStatus.UNPROCESSABLE_ENTITY, "document_publish_invalid", exception.getMessage(), List.of());
    }

    @ExceptionHandler(AssetDocumentRelationConflictException.class)
    ResponseEntity<ApiError> handleAssetDocumentRelationConflict(AssetDocumentRelationConflictException exception) {
        return response(HttpStatus.CONFLICT, "asset_document_relation_conflict", exception.getMessage(), List.of());
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
