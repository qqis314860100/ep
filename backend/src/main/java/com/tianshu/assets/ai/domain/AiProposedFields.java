package com.tianshu.assets.ai.domain;

import java.util.List;

/**
 * AI 抽取出的建议字段（命名/分类/标签/摘要等），按目标类型解释：
 * 资产目标使用 name/description/assetTypeCode/tags；知识文档目标使用 title(->name 语义)/summary/categoryCode。
 * 空值/空串表示「该字段无建议、不覆盖」。
 */
public record AiProposedFields(
        String name,
        String description,
        String assetTypeCode,
        List<String> tags,
        String summary,
        String categoryCode) {

    public AiProposedFields {
        name = text(name);
        description = text(description);
        assetTypeCode = text(assetTypeCode);
        summary = text(summary);
        categoryCode = text(categoryCode);
        tags = tags == null ? List.of() : List.copyOf(tags);
    }

    public boolean hasAssetChanges() {
        return !name.isBlank() || !description.isBlank() || !assetTypeCode.isBlank() || !tags.isEmpty();
    }

    public boolean hasDocChanges() {
        return !name.isBlank() || !summary.isBlank() || !categoryCode.isBlank();
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
