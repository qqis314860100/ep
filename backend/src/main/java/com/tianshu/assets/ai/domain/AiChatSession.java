package com.tianshu.assets.ai.domain;

import java.time.Instant;

/** AI 问答会话（库为真源，T3）：归属用户，标题取首问截断。 */
public record AiChatSession(
        long id,
        String userId,
        String title,
        Instant createdAt,
        Instant updatedAt) {

    public AiChatSession {
        userId = userId == null ? "" : userId.trim();
        title = title == null ? "" : title.trim();
    }
}
