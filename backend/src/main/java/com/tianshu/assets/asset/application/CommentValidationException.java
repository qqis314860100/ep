package com.tianshu.assets.asset.application;

public class CommentValidationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public CommentValidationException() {
        super("评论内容不能为空");
    }

    public CommentValidationException(String message) {
        super(message);
    }
}
