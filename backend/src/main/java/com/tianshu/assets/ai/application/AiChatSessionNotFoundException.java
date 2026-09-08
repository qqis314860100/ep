package com.tianshu.assets.ai.application;

/** 会话不存在或不属于当前用户（统一 404，避免存在性泄露）。 */
public class AiChatSessionNotFoundException extends RuntimeException {

    public AiChatSessionNotFoundException(String message) {
        super(message);
    }
}
