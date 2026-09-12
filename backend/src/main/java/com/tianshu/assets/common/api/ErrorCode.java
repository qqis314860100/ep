package com.tianshu.assets.common.api;

import org.springframework.http.HttpStatus;

/**
 * The single registry of machine-readable API error codes.
 *
 * <p>Each code is declared exactly once here and may be referenced by more than
 * one exception handler when the client-facing meaning is the same. Uniqueness
 * of {@link #code()} is asserted by ErrorCodeTest.
 */
public enum ErrorCode {
    ASSET_NOT_FOUND("asset_not_found", HttpStatus.NOT_FOUND),
    DUPLICATE_ASSET_NUMBER("duplicate_asset_number", HttpStatus.CONFLICT),
    ASSET_SUBMISSION_INVALID("asset_submission_invalid", HttpStatus.UNPROCESSABLE_ENTITY),
    COMMENT_INVALID("comment_invalid", HttpStatus.UNPROCESSABLE_ENTITY),
    FILE_INVALID("file_invalid", HttpStatus.UNPROCESSABLE_ENTITY),
    OPERATION_FORBIDDEN("operation_forbidden", HttpStatus.FORBIDDEN),
    READ_ONLY_ADAPTER("read_only_adapter", HttpStatus.CONFLICT),

    GOVERNANCE_STATE_CONFLICT("governance_state_conflict", HttpStatus.CONFLICT),
    GOVERNANCE_NOT_FOUND("governance_not_found", HttpStatus.NOT_FOUND),
    GOVERNANCE_FORBIDDEN("governance_forbidden", HttpStatus.FORBIDDEN),
    GOVERNANCE_VERSION_CONFLICT("governance_version_conflict", HttpStatus.CONFLICT),
    GOVERNANCE_VALIDATION_FAILED("governance_validation_failed", HttpStatus.UNPROCESSABLE_ENTITY),

    ASSET_RELATION_VERSION_CONFLICT("asset_relation_version_conflict", HttpStatus.CONFLICT),
    ASSET_RELATION_CONFLICT("asset_relation_conflict", HttpStatus.CONFLICT),
    ASSET_DOCUMENT_RELATION_CONFLICT("asset_document_relation_conflict", HttpStatus.CONFLICT),

    DICTIONARY_ITEM_NOT_FOUND("dictionary_item_not_found", HttpStatus.NOT_FOUND),
    DICTIONARY_ITEM_CONFLICT("dictionary_item_conflict", HttpStatus.CONFLICT),

    DOCUMENT_NOT_FOUND("document_not_found", HttpStatus.NOT_FOUND),
    DUPLICATE_DOCUMENT_NUMBER("duplicate_document_number", HttpStatus.CONFLICT),
    DOCUMENT_STATE_CONFLICT("document_state_conflict", HttpStatus.CONFLICT),
    DOCUMENT_PUBLISH_INVALID("document_publish_invalid", HttpStatus.UNPROCESSABLE_ENTITY),

    SYSTEM_USER_NOT_FOUND("system_user_not_found", HttpStatus.NOT_FOUND),
    SYSTEM_USER_CONFLICT("system_user_conflict", HttpStatus.CONFLICT),
    AUTH_FAILED("auth_failed", HttpStatus.UNAUTHORIZED),

    AI_SUGGESTION_NOT_FOUND("ai_suggestion_not_found", HttpStatus.NOT_FOUND),
    AI_SUGGESTION_STATE_CONFLICT("ai_suggestion_state_conflict", HttpStatus.CONFLICT),
    AI_SUGGESTION_SCOPE_FORBIDDEN("ai_suggestion_scope_forbidden", HttpStatus.FORBIDDEN),
    AI_SUGGESTION_INVALID("ai_suggestion_invalid", HttpStatus.UNPROCESSABLE_ENTITY),
    AI_CHAT_SESSION_NOT_FOUND("ai_chat_session_not_found", HttpStatus.NOT_FOUND),
    AI_CHAT_INVALID("ai_chat_invalid", HttpStatus.UNPROCESSABLE_ENTITY),
    AI_CAPABILITY_UNAVAILABLE("ai_capability_unavailable", HttpStatus.SERVICE_UNAVAILABLE),

    INVALID_REQUEST("invalid_request", HttpStatus.UNPROCESSABLE_ENTITY),
    FILE_TOO_LARGE("file_too_large", HttpStatus.PAYLOAD_TOO_LARGE),
    VALIDATION_ERROR("validation_error", HttpStatus.BAD_REQUEST);

    private final String code;
    private final HttpStatus status;

    ErrorCode(String code, HttpStatus status) {
        this.code = code;
        this.status = status;
    }

    public String code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }
}
