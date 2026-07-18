package com.tianshu.assets.asset.api;

import com.tianshu.assets.asset.application.AssetQueryService;
import com.tianshu.assets.asset.application.AssetWriteService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/favorites")
public class FavoriteController {

    private final AssetQueryService assetQueryService;
    private final AssetWriteService assetWriteService;

    public FavoriteController(AssetQueryService assetQueryService, AssetWriteService assetWriteService) {
        this.assetQueryService = assetQueryService;
        this.assetWriteService = assetWriteService;
    }

    @GetMapping
    public List<AssetResponse> list(
            @RequestHeader(name = "X-User-Id", defaultValue = "demo-user") String userId) {
        return assetWriteService.favoriteAssetIds(userId).stream()
                .map(assetQueryService::getOptional)
                .flatMap(java.util.Optional::stream)
                .map(AssetResponse::from)
                .toList();
    }
}
