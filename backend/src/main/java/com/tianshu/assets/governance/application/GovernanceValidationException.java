package com.tianshu.assets.governance.application;

import java.util.List;

public class GovernanceValidationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient List<String> validationMessages;

    public GovernanceValidationException(String message) {
        this(List.of(message));
    }

    public GovernanceValidationException(List<String> validationMessages) {
        super(String.join("；", validationMessages));
        this.validationMessages = List.copyOf(validationMessages);
    }

    public List<String> validationMessages() {
        return validationMessages;
    }
}
