package com.tianshu.assets.common.api;

import java.util.List;

public record ApiError(ErrorBody error) {

    public record ErrorBody(String code, String message, List<FieldError> details) {
        public ErrorBody {
            details = details == null ? List.of() : List.copyOf(details);
        }
    }

    public record FieldError(String field, String message, String code) {}
}
