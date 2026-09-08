package com.tianshu.assets.ai.domain;

/** AI 建议作用的目标类型。当前应用侧仅支持资产目标，文档目标随入库触发切片落地。 */
public enum AiSuggestionTargetType {
    ASSET,
    KNOWLEDGE_DOC
}
