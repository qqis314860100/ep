package com.tianshu.assets.ai.domain;

import java.util.List;
import java.util.Optional;

/** AI 问答会话/消息仓储：dev 内存实现 / local+oceanbase JDBC 实现。 */
public interface AiChatRepository {

    AiChatSession saveSession(AiChatSession session);

    AiChatSession updateSession(AiChatSession session);

    Optional<AiChatSession> findSession(long id);

    /** 某用户的会话，按更新时间倒序。 */
    List<AiChatSession> listSessions(String userId);

    /** 删除会话（含其消息）。 */
    void deleteSession(long sessionId);

    AiChatMessage saveMessage(AiChatMessage message);

    /** 会话消息，按 id 升序。 */
    List<AiChatMessage> listMessages(long sessionId);
}
