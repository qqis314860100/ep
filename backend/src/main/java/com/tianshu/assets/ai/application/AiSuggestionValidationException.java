package com.tianshu.assets.ai.application;

/** 建议登记/参数校验失败。 */
public class AiSuggestionValidationException extends RuntimeException {

    public AiSuggestionValidationException(String message) {
        super(message);
    }
}
