package com.tianshu.assets.dictionary.application;

import java.util.Map;

/**
 * 无统计能力的默认实现，用于测试便捷构造与不启用实时统计的场景。
 */
public final class NoopDictionaryUsageCounter implements DictionaryUsageCounter {

    @Override
    public Map<String, Long> countsByCategory(String category) {
        return Map.of();
    }
}
