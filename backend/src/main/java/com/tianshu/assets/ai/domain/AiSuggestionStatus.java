package com.tianshu.assets.ai.domain;

/** AI 建议生命周期状态：待确认 -> 已确认 | 已驳回；重复整理使旧待确认建议作废。 */
public enum AiSuggestionStatus {
    PENDING,
    CONFIRMED,
    REJECTED,
    SUPERSEDED
}
