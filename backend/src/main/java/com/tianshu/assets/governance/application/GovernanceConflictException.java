package com.tianshu.assets.governance.application;

public class GovernanceConflictException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public GovernanceConflictException(String message) {
        super(message);
    }
}
