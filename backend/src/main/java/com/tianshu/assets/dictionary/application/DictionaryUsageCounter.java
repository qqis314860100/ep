package com.tianshu.assets.dictionary.application;

import java.util.Map;

/**
 * 字典项引用资产数量统计端口。按字典分类统计每个取值被多少份资产引用，
 * 使基础数据页的「引用资产」数量与资产仓储保持一致。
 */
public interface DictionaryUsageCounter {

    /**
     * 返回指定分类下 取值 -> 引用资产数量 的映射。
     * 键与字典项的匹配字段约定：资产字段类（专业/标签/范围/文件角色）按名称匹配，
     * 资产类型按字典编码（即 {@code AssetType} 枚举名）匹配。
     */
    Map<String, Long> countsByCategory(String category);
}
