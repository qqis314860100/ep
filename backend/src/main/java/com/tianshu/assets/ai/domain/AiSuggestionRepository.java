package com.tianshu.assets.ai.domain;

import java.util.List;
import java.util.Optional;

/** AI 建议仓储：dev 内存实现 / local+oceanbase JDBC 实现沿袭既有仓储先例。 */
public interface AiSuggestionRepository {

    AiSuggestion save(AiSuggestion suggestion);

    /** 乐观更新：期望版本不匹配抛 IllegalStateException。 */
    AiSuggestion update(AiSuggestion suggestion, long expectedVersion);

    Optional<AiSuggestion> findById(long id);

    List<AiSuggestion> findByTarget(AiSuggestionTargetType targetType, long targetId);

    /** 全量（按 id 升序），由应用层做范围过滤与分页；规模小、治理期够用。 */
    List<AiSuggestion> findAll();
}
