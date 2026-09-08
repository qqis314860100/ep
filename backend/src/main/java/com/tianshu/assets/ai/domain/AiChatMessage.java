package com.tianshu.assets.ai.domain;

import java.time.Instant;
import java.util.List;

/** 会话消息（库为真源，T3）：用户提问即时落库，助手回答在流结束后整体落库（含引用）。 */
public record AiChatMessage(
        long id,
        long sessionId,
        AiChatMessageRole role,
        String content,
        List<AiCitation> citations,
        Instant createdAt) {

    public AiChatMessage {
        content = content == null ? "" : content;
        citations = citations == null ? List.of() : List.copyOf(citations);
    }
}
