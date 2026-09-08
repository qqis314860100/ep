package com.tianshu.assets.ai.application;

/** 会话操作参数/状态校验失败（AI 问答域）。 */
public class AiChatValidationException extends RuntimeException {

    public AiChatValidationException(String message) {
        super(message);
    }
}
