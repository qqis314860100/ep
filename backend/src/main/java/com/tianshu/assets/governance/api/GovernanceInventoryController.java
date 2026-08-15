package com.tianshu.assets.governance.api;

import com.tianshu.assets.governance.inventory.application.AssetInventoryService;
import com.tianshu.assets.governance.inventory.application.AssetInventoryService.InventoryView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 资产盘点（GOVERN-01）：总量/治理率/旧维度与缺字段筛选。 */
@RestController
@RequestMapping("/api/v1/governance/inventory")
public class GovernanceInventoryController {

    private final AssetInventoryService service;

    @Autowired
    public GovernanceInventoryController(AssetInventoryService service) {
        this.service = service;
    }

    @GetMapping
    public InventoryView inventory(
            @RequestParam(name = "legacy_platform", defaultValue = "") String legacyPlatform,
            @RequestParam(name = "legacy_line", defaultValue = "") String legacyLine,
            @RequestParam(name = "legacy_category", defaultValue = "") String legacyCategory,
            @RequestParam(defaultValue = "") String owner,
            @RequestParam(defaultValue = "") String format,
            @RequestParam(name = "missing_base", defaultValue = "false") boolean missingBase,
            @RequestParam(name = "missing_line", defaultValue = "false") boolean missingLine,
            @RequestParam(name = "missing_description", defaultValue = "false") boolean missingDescription,
            @RequestParam(name = "missing_owner", defaultValue = "false") boolean missingOwner,
            @RequestParam(name = "missing_file", defaultValue = "false") boolean missingFile,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "per_page", defaultValue = "20") int perPage) {
        return service.inventory(legacyPlatform, legacyLine, legacyCategory, owner, format,
                missingBase, missingLine, missingDescription, missingOwner, missingFile, page, perPage);
    }
}
