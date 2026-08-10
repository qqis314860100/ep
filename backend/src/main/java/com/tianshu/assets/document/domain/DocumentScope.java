package com.tianshu.assets.document.domain;

public record DocumentScope(
        long id,
        long documentId,
        String platformFamily,
        String platformVariant,
        String productLine,
        String baseName,
        String productionLine,
        String processSection) {

    public DocumentScope {
        platformFamily = text(platformFamily);
        platformVariant = text(platformVariant);
        productLine = text(productLine);
        baseName = text(baseName);
        productionLine = text(productionLine);
        processSection = text(processSection);
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
