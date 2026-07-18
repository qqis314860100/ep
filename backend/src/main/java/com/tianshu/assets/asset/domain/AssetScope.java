package com.tianshu.assets.asset.domain;

public record AssetScope(
        String platform,
        String productLine,
        String base,
        String productionLine,
        String processSection,
        String platformFamily,
        String platformVariant) {

    public AssetScope(String platform, String productLine, String base, String productionLine, String processSection) {
        this(platform, productLine, base, productionLine, processSection, platform, "");
    }

    public AssetScope {
        platform = platform == null ? "" : platform;
        productLine = productLine == null ? "" : productLine;
        base = base == null ? "" : base;
        productionLine = productionLine == null ? "" : productionLine;
        processSection = processSection == null ? "" : processSection;
        platformFamily = platformFamily == null || platformFamily.isBlank() ? platform : platformFamily;
        platformVariant = platformVariant == null ? "" : platformVariant;
    }
}
