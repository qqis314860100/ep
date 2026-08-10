package com.tianshu.assets.document.domain;

public record DocumentSearchCriteria(
        String query,
        String categoryCode,
        String platformFamily,
        String platformVariant,
        String productLine,
        String baseName,
        String productionLine,
        String processSection,
        int page,
        int perPage) {

    public DocumentSearchCriteria {
        query = query == null ? "" : query.trim();
        categoryCode = categoryCode == null ? "" : categoryCode.trim();
        platformFamily = text(platformFamily);
        platformVariant = text(platformVariant);
        productLine = text(productLine);
        baseName = text(baseName);
        productionLine = text(productionLine);
        processSection = text(processSection);
        page = Math.max(page, 1);
        perPage = Math.min(Math.max(perPage, 1), 100);
    }

    public DocumentSearchCriteria(String query, String categoryCode, int page, int perPage) {
        this(query, categoryCode, "", "", "", "", "", "", page, perPage);
    }

    public boolean hasScopeFilter() {
        return !platformFamily.isBlank() || !platformVariant.isBlank() || !productLine.isBlank()
                || !baseName.isBlank() || !productionLine.isBlank() || !processSection.isBlank();
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
