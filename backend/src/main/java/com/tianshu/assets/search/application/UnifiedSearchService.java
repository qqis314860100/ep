package com.tianshu.assets.search.application;

import com.tianshu.assets.asset.application.AssetQueryService;
import com.tianshu.assets.asset.domain.AssetSearchCriteria;
import com.tianshu.assets.document.application.DocumentQueryService;
import com.tianshu.assets.document.domain.DocumentSearchCriteria;
import com.tianshu.assets.search.api.UnifiedSearchResponse;
import org.springframework.stereotype.Service;

@Service
public class UnifiedSearchService {

    private final AssetQueryService assets;
    private final DocumentQueryService documents;

    public UnifiedSearchService(AssetQueryService assets, DocumentQueryService documents) {
        this.assets = assets;
        this.documents = documents;
    }

    public UnifiedSearchResponse search(Criteria criteria) {
        var assetPage = assets.search(new AssetSearchCriteria(criteria.query(), null, null, "",
                criteria.platformFamily(), criteria.platformVariant(), criteria.baseName(), criteria.productionLine(),
                null, criteria.assetPage(), criteria.assetPerPage(), criteria.productLine(), criteria.processSection()));
        var documentPage = documents.search(new DocumentSearchCriteria(criteria.query(), "", criteria.platformFamily(),
                criteria.platformVariant(), criteria.productLine(), criteria.baseName(), criteria.productionLine(),
                criteria.processSection(), criteria.documentPage(), criteria.documentPerPage()));
        return UnifiedSearchResponse.from(assetPage, documentPage);
    }

    public record Criteria(String query, String platformFamily, String platformVariant, String productLine,
            String baseName, String productionLine, String processSection, int assetPage, int assetPerPage,
            int documentPage, int documentPerPage) {
        public Criteria {
            query = text(query);
            platformFamily = text(platformFamily);
            platformVariant = text(platformVariant);
            productLine = text(productLine);
            baseName = text(baseName);
            productionLine = text(productionLine);
            processSection = text(processSection);
            assetPage = Math.max(assetPage, 1);
            documentPage = Math.max(documentPage, 1);
            assetPerPage = Math.clamp(assetPerPage, 1, 100);
            documentPerPage = Math.clamp(documentPerPage, 1, 100);
        }

        private static String text(String value) { return value == null ? "" : value.trim(); }
    }
}
