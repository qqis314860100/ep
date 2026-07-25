package com.tianshu.assets.document.application;

public class DocumentStateConflictException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DocumentStateConflictException(String message) {
        super(message);
    }

    public DocumentStateConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
