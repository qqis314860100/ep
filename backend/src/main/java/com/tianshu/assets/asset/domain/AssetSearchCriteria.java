package com.tianshu.assets.asset.domain;

import java.time.Instant;
import java.util.Locale;

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
        String processSection,
        String specialty,
        String fileFormat,
        Instant updatedFrom,
        Instant updatedTo,
        Boolean missingScope,
        String sort) {

    public AssetSearchCriteria {
        query = query == null ? "" : query.trim();
        platformFamily = platformFamily == null ? "" : platformFamily.trim();
        platformVariant = platformVariant == null ? "" : platformVariant.trim();
        ownerName = ownerName == null ? "" : ownerName.trim();
        base = base == null ? "" : base.trim();
        productionLine = productionLine == null ? "" : productionLine.trim();
        productLine = productLine == null ? "" : productLine.trim();
        processSection = processSection == null ? "" : processSection.trim();
        specialty = specialty == null ? "" : specialty.trim();
        fileFormat = fileFormat == null ? "" : fileFormat.trim().toUpperCase(Locale.ROOT);
        sort = sort == null || sort.isBlank() ? "RELEVANCE" : sort.trim().toUpperCase(Locale.ROOT);
    }

    public AssetSearchCriteria(String query, AssetType assetType, AssetStatus status, String ownerName,
            String platformFamily, String platformVariant, String base, String productionLine, Boolean previewable,
            int page, int perPage, String productLine, String processSection) {
        this(query, assetType, status, ownerName, platformFamily, platformVariant, base, productionLine,
                previewable, page, perPage, productLine, processSection, "", "", null, null, null, "RELEVANCE");
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

    public boolean wantsMissingScope() {
        return Boolean.TRUE.equals(missingScope);
    }
}
