package com.tianshu.assets.governance.infrastructure;

import com.tianshu.assets.governance.confirmation.application.AssetResponsibilityPort;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryAssetResponsibilityAdapter implements AssetResponsibilityPort {

    private final Map<Long, AssetResponsibility> responsibilities = new ConcurrentHashMap<>();

    public void assign(long assetId, String responsibleUserId, String responsibilityScope) {
        responsibilities.put(assetId,
                new AssetResponsibility(assetId, responsibleUserId, responsibilityScope));
    }

    @Override
    public Optional<AssetResponsibility> currentResponsibility(long assetId) {
        return Optional.ofNullable(responsibilities.get(assetId));
    }
}
