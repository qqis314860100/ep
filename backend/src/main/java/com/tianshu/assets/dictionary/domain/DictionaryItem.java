package com.tianshu.assets.dictionary.domain;

import java.time.LocalDateTime;

public record DictionaryItem(
        long id,
        String category,
        String code,
        String name,
        Long parentId,
        DictionaryStatus status,
        int sortOrder,
        long usageCount,
        long version,
        String description,
        String forwardName,
        String reverseName,
        boolean directional,
        boolean allowDuplicate,
        Long mergeTargetId,
        LocalDateTime updatedAt) {}
