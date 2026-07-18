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
        int page,
        int perPage) {

    public AssetSearchCriteria {
        query = query == null ? "" : query.trim();
        platformFamily = platformFamily == null ? "" : platformFamily.trim();
        platformVariant = platformVariant == null ? "" : platformVariant.trim();
        ownerName = ownerName == null ? "" : ownerName.trim();
    }

    public AssetSearchCriteria(String query, AssetType assetType, AssetStatus status,
            String base, String productionLine, int page, int perPage) {
        this(query, assetType, status, "", "", "", base, productionLine, page, perPage);
    }
}
