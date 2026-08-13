package com.tianshu.assets.system.application;

public class SystemUserConflictException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SystemUserConflictException(String message) {
        super(message);
    }
}
