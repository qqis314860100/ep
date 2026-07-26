package com.tianshu.assets.governance.application;

public class GovernanceVersionConflictException extends GovernanceConflictException {

    private static final long serialVersionUID = 1L;

    public GovernanceVersionConflictException(String message) {
        super(message);
    }
}
