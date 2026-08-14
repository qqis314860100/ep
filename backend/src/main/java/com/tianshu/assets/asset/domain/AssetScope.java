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

    /** 是否具备标准完整适用范围（平台族/子类、蓝本、基地、拉线、工序段均非空）。 */
    public boolean complete() {
        return !platformFamily.isBlank() && !platformVariant.isBlank() && !productLine.isBlank()
                && !base.isBlank() && !productionLine.isBlank() && !processSection.isBlank();
    }
}
