package com.tianshu.assets.search.api;

import com.tianshu.assets.search.application.UnifiedSearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/search")
public class UnifiedSearchController {

    private final UnifiedSearchService service;

    public UnifiedSearchController(UnifiedSearchService service) {
        this.service = service;
    }

    @GetMapping
    public UnifiedSearchResponse search(
            @RequestParam(name = "q", defaultValue = "") String query,
            @RequestParam(name = "platform_family", defaultValue = "") String platformFamily,
            @RequestParam(name = "platform_variant", defaultValue = "") String platformVariant,
            @RequestParam(name = "product_line", defaultValue = "") String productLine,
            @RequestParam(name = "base", defaultValue = "") String baseName,
            @RequestParam(name = "production_line", defaultValue = "") String productionLine,
            @RequestParam(name = "process_section", defaultValue = "") String processSection,
            @RequestParam(name = "asset_page", defaultValue = "1") int assetPage,
            @RequestParam(name = "asset_per_page", defaultValue = "12") int assetPerPage,
            @RequestParam(name = "document_page", defaultValue = "1") int documentPage,
            @RequestParam(name = "document_per_page", defaultValue = "8") int documentPerPage) {
        return service.search(new UnifiedSearchService.Criteria(query, platformFamily, platformVariant, productLine,
                baseName, productionLine, processSection, assetPage, assetPerPage, documentPage, documentPerPage));
    }
}
