package com.tianshu.assets.governance.application;

public class GovernanceNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public GovernanceNotFoundException(String message) {
        super(message);
    }
}
