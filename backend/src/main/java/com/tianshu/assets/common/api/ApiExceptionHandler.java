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
import com.tianshu.assets.system.application.SystemUserConflictException;
import com.tianshu.assets.system.application.SystemUserNotFoundException;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(AssetNotFoundException.class)
    ResponseEntity<ApiError> handleNotFound(AssetNotFoundException exception) {
        return response(ErrorCode.ASSET_NOT_FOUND, exception.getMessage(), List.of());
    }

    @ExceptionHandler(DuplicateAssetNumberException.class)
    ResponseEntity<ApiError> handleDuplicate(DuplicateAssetNumberException exception) {
        return response(ErrorCode.DUPLICATE_ASSET_NUMBER, exception.getMessage(), List.of());
    }

    @ExceptionHandler(AssetSubmissionValidationException.class)
    ResponseEntity<ApiError> handleSubmissionValidation(AssetSubmissionValidationException exception) {
        return response(ErrorCode.ASSET_SUBMISSION_INVALID, exception.getMessage(), List.of());
    }

    @ExceptionHandler(CommentValidationException.class)
    ResponseEntity<ApiError> handleCommentValidation(CommentValidationException exception) {
        return response(ErrorCode.COMMENT_INVALID, exception.getMessage(), List.of());
    }

    @ExceptionHandler(AssetFileValidationException.class)
    ResponseEntity<ApiError> handleFileValidation(AssetFileValidationException exception) {
        return response(ErrorCode.FILE_INVALID, exception.getMessage(), List.of());
    }

    @ExceptionHandler(ForbiddenOperationException.class)
    ResponseEntity<ApiError> handleForbidden(ForbiddenOperationException exception) {
        return response(ErrorCode.OPERATION_FORBIDDEN, exception.getMessage(), List.of());
    }

    @ExceptionHandler(UnsupportedOperationException.class)
    ResponseEntity<ApiError> handleReadOnly(UnsupportedOperationException exception) {
        return response(ErrorCode.READ_ONLY_ADAPTER, exception.getMessage(), List.of());
    }

    @ExceptionHandler(GovernanceTaskStateException.class)
    ResponseEntity<ApiError> handleGovernanceStateConflict(GovernanceTaskStateException exception) {
        return response(ErrorCode.GOVERNANCE_STATE_CONFLICT, exception.getMessage(), List.of());
    }

    @ExceptionHandler(GovernanceConflictException.class)
    ResponseEntity<ApiError> handleGovernanceConflict(GovernanceConflictException exception) {
        return response(ErrorCode.GOVERNANCE_STATE_CONFLICT, exception.getMessage(), List.of());
    }

    @ExceptionHandler(GovernanceNotFoundException.class)
    ResponseEntity<ApiError> handleGovernanceNotFound(GovernanceNotFoundException exception) {
        return response(ErrorCode.GOVERNANCE_NOT_FOUND, exception.getMessage(), List.of());
    }

    @ExceptionHandler(GovernanceAuthorizationException.class)
    ResponseEntity<ApiError> handleGovernanceForbidden(GovernanceAuthorizationException exception) {
        return response(ErrorCode.GOVERNANCE_FORBIDDEN, exception.getMessage(), List.of());
    }

    @ExceptionHandler(GovernanceVersionConflictException.class)
    ResponseEntity<ApiError> handleGovernanceVersionConflict(GovernanceVersionConflictException exception) {
        return response(ErrorCode.GOVERNANCE_VERSION_CONFLICT, exception.getMessage(), List.of());
    }

    @ExceptionHandler(com.tianshu.assets.asset.application.AssetRelationVersionConflictException.class)
    ResponseEntity<ApiError> handleRelationVersionConflict(
            com.tianshu.assets.asset.application.AssetRelationVersionConflictException exception) {
        return response(ErrorCode.ASSET_RELATION_VERSION_CONFLICT, exception.getMessage(), List.of());
    }

    @ExceptionHandler(com.tianshu.assets.asset.application.AssetRelationConflictException.class)
    ResponseEntity<ApiError> handleRelationConflict(
            com.tianshu.assets.asset.application.AssetRelationConflictException exception) {
        return response(ErrorCode.ASSET_RELATION_CONFLICT, exception.getMessage(), List.of());
    }

    @ExceptionHandler(GovernanceValidationException.class)
    ResponseEntity<ApiError> handleGovernanceValidation(GovernanceValidationException exception) {
        var details = exception.validationMessages().stream()
                .map(message -> new ApiError.FieldError("governance", message, "invalid_state"))
                .toList();
        return response(ErrorCode.GOVERNANCE_VALIDATION_FAILED, exception.getMessage(), details);
    }

    @ExceptionHandler(DictionaryNotFoundException.class)
    ResponseEntity<ApiError> handleDictionaryNotFound(DictionaryNotFoundException exception) {
        return response(ErrorCode.DICTIONARY_ITEM_NOT_FOUND, exception.getMessage(), List.of());
    }

    @ExceptionHandler(DictionaryConflictException.class)
    ResponseEntity<ApiError> handleDictionaryConflict(DictionaryConflictException exception) {
        return response(ErrorCode.DICTIONARY_ITEM_CONFLICT, exception.getMessage(), List.of());
    }

    @ExceptionHandler(DocumentNotFoundException.class)
    ResponseEntity<ApiError> handleDocumentNotFound(DocumentNotFoundException exception) {
        return response(ErrorCode.DOCUMENT_NOT_FOUND, exception.getMessage(), List.of());
    }

    @ExceptionHandler(DuplicateDocumentNumberException.class)
    ResponseEntity<ApiError> handleDuplicateDocumentNumber(DuplicateDocumentNumberException exception) {
        return response(ErrorCode.DUPLICATE_DOCUMENT_NUMBER, exception.getMessage(), List.of());
    }

    @ExceptionHandler(DocumentStateConflictException.class)
    ResponseEntity<ApiError> handleDocumentStateConflict(DocumentStateConflictException exception) {
        return response(ErrorCode.DOCUMENT_STATE_CONFLICT, exception.getMessage(), List.of());
    }

    @ExceptionHandler(DocumentPublishValidationException.class)
    ResponseEntity<ApiError> handleDocumentPublishValidation(DocumentPublishValidationException exception) {
        return response(ErrorCode.DOCUMENT_PUBLISH_INVALID, exception.getMessage(), List.of());
    }

    @ExceptionHandler(AssetDocumentRelationConflictException.class)
    ResponseEntity<ApiError> handleAssetDocumentRelationConflict(AssetDocumentRelationConflictException exception) {
        return response(ErrorCode.ASSET_DOCUMENT_RELATION_CONFLICT, exception.getMessage(), List.of());
    }

    @ExceptionHandler(SystemUserNotFoundException.class)
    ResponseEntity<ApiError> handleSystemUserNotFound(SystemUserNotFoundException exception) {
        return response(ErrorCode.SYSTEM_USER_NOT_FOUND, exception.getMessage(), List.of());
    }

    @ExceptionHandler(SystemUserConflictException.class)
    ResponseEntity<ApiError> handleSystemUserConflict(SystemUserConflictException exception) {
        return response(ErrorCode.SYSTEM_USER_CONFLICT, exception.getMessage(), List.of());
    }

    @ExceptionHandler(com.tianshu.assets.system.application.AuthException.class)
    ResponseEntity<ApiError> handleAuth(com.tianshu.assets.system.application.AuthException exception) {
        return response(ErrorCode.AUTH_FAILED, exception.getMessage(), List.of());
    }

    @ExceptionHandler(com.tianshu.assets.ai.application.AiSuggestionNotFoundException.class)
    ResponseEntity<ApiError> handleAiSuggestionNotFound(
            com.tianshu.assets.ai.application.AiSuggestionNotFoundException exception) {
        return response(ErrorCode.AI_SUGGESTION_NOT_FOUND, exception.getMessage(), List.of());
    }

    @ExceptionHandler(com.tianshu.assets.ai.application.AiSuggestionStateException.class)
    ResponseEntity<ApiError> handleAiSuggestionStateConflict(
            com.tianshu.assets.ai.application.AiSuggestionStateException exception) {
        return response(ErrorCode.AI_SUGGESTION_STATE_CONFLICT, exception.getMessage(), List.of());
    }

    @ExceptionHandler(com.tianshu.assets.ai.application.AiSuggestionScopeException.class)
    ResponseEntity<ApiError> handleAiSuggestionScopeForbidden(
            com.tianshu.assets.ai.application.AiSuggestionScopeException exception) {
        return response(ErrorCode.AI_SUGGESTION_SCOPE_FORBIDDEN, exception.getMessage(), List.of());
    }

    @ExceptionHandler(com.tianshu.assets.ai.application.AiSuggestionValidationException.class)
    ResponseEntity<ApiError> handleAiSuggestionValidation(
            com.tianshu.assets.ai.application.AiSuggestionValidationException exception) {
        return response(ErrorCode.AI_SUGGESTION_INVALID, exception.getMessage(), List.of());
    }

    @ExceptionHandler(com.tianshu.assets.ai.application.AiChatSessionNotFoundException.class)
    ResponseEntity<ApiError> handleAiChatSessionNotFound(
            com.tianshu.assets.ai.application.AiChatSessionNotFoundException exception) {
        return response(ErrorCode.AI_CHAT_SESSION_NOT_FOUND, exception.getMessage(), List.of());
    }

    @ExceptionHandler(com.tianshu.assets.ai.application.AiChatValidationException.class)
    ResponseEntity<ApiError> handleAiChatValidation(
            com.tianshu.assets.ai.application.AiChatValidationException exception) {
        return response(ErrorCode.AI_CHAT_INVALID, exception.getMessage(), List.of());
    }

    @ExceptionHandler(com.tianshu.assets.ai.application.AiCapabilityException.class)
    ResponseEntity<ApiError> handleAiCapability(
            com.tianshu.assets.ai.application.AiCapabilityException exception) {
        return response(ErrorCode.AI_CAPABILITY_UNAVAILABLE, exception.getMessage(), List.of());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException exception) {
        return response(ErrorCode.INVALID_REQUEST, exception.getMessage(), List.of());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiError> handleMaxUpload(MaxUploadSizeExceededException exception) {
        return response(ErrorCode.FILE_TOO_LARGE, "单个文件或上传批次超过大小限制", List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception) {
        var details = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiError.FieldError(
                        error.getField(), error.getDefaultMessage(), "invalid_value"))
                .toList();
        return response(ErrorCode.VALIDATION_ERROR, "请求参数校验失败", details);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException exception) {
        var details = exception.getConstraintViolations().stream()
                .map(violation -> new ApiError.FieldError(
                        violation.getPropertyPath().toString(),
                        violation.getMessage(),
                        "invalid_value"))
                .toList();
        return response(ErrorCode.VALIDATION_ERROR, "请求参数校验失败", details);
    }

    private ResponseEntity<ApiError> response(
            ErrorCode errorCode, String message, List<ApiError.FieldError> details) {
        return ResponseEntity.status(errorCode.status())
                .body(new ApiError(new ApiError.ErrorBody(errorCode.code(), message, details)));
    }
}
