package com.tianshu.assets.ai.application;

/** 建议状态冲突：非待确认不可确认/驳回、并发更新等。 */
public class AiSuggestionStateException extends RuntimeException {

    public AiSuggestionStateException(String message) {
        super(message);
    }
}
