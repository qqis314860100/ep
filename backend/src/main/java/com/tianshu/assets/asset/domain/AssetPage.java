package com.tianshu.assets.asset.domain;

import java.util.List;

public record AssetPage(List<Asset> items, long total, int page, int perPage) {

    public AssetPage {
        items = List.copyOf(items);
    }
}
