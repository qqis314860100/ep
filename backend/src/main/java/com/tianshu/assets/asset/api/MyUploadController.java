package com.tianshu.assets.asset.api;

import com.tianshu.assets.asset.application.AssetQueryService;
import com.tianshu.assets.asset.domain.AssetSearchCriteria;
import com.tianshu.assets.asset.domain.AssetStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/uploads")
public class MyUploadController {

    private final AssetQueryService assetQueryService;

    public MyUploadController(AssetQueryService assetQueryService) {
        this.assetQueryService = assetQueryService;
    }

    @GetMapping("/mine")
    public PageResponse<AssetResponse> mine(
            @RequestHeader(name = "X-User-Name", defaultValue = "陈工") String ownerName,
            @RequestParam(required = false) AssetStatus status,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(name = "per_page", defaultValue = "20") @Min(1) @Max(100) int perPage) {
        var result = assetQueryService.search(new AssetSearchCriteria("", null, status, ownerName, "", "", "", "", null, page, perPage));
        return new PageResponse<>(result.items().stream().map(AssetResponse::from).toList(),
                PageResponse.Meta.of(result.total(), result.page(), result.perPage()));
    }
}
