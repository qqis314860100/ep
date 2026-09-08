package com.tianshu.assets.ai.application;

/** 越权：建议目标不在当前用户 AssetScope 内。 */
public class AiSuggestionScopeException extends RuntimeException {

    public AiSuggestionScopeException(String message) {
        super(message);
    }
}
