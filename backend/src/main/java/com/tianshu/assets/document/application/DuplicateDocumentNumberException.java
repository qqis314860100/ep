package com.tianshu.assets.document.application;

public class DuplicateDocumentNumberException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DuplicateDocumentNumberException(String message) {
        super(message);
    }
}
