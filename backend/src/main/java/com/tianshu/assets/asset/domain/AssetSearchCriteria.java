package com.tianshu.assets.asset.domain;

public record AssetSearchCriteria(
        String query,
        AssetType assetType,
        AssetStatus status,
        String ownerName,
        String platformFamily,
        String platformVariant,
        String base,
        String productionLine,
        Boolean previewable,
        int page,
        int perPage,
        String productLine,
        String processSection) {

    public AssetSearchCriteria {
        query = query == null ? "" : query.trim();
        platformFamily = platformFamily == null ? "" : platformFamily.trim();
        platformVariant = platformVariant == null ? "" : platformVariant.trim();
        ownerName = ownerName == null ? "" : ownerName.trim();
        productLine = productLine == null ? "" : productLine.trim();
        processSection = processSection == null ? "" : processSection.trim();
    }

    public AssetSearchCriteria(String query, AssetType assetType, AssetStatus status, String ownerName,
            String platformFamily, String platformVariant, String base, String productionLine, Boolean previewable,
            int page, int perPage) {
        this(query, assetType, status, ownerName, platformFamily, platformVariant, base, productionLine,
                previewable, page, perPage, "", "");
    }

    public AssetSearchCriteria(String query, AssetType assetType, AssetStatus status,
            String base, String productionLine, int page, int perPage) {
        this(query, assetType, status, "", "", "", base, productionLine, null, page, perPage, "", "");
    }
}
