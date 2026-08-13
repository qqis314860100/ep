package com.tianshu.assets.governance.infrastructure;

import com.tianshu.assets.governance.confirmation.application.AssetResponsibilityPort;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryAssetResponsibilityAdapter implements AssetResponsibilityPort {

    private final Map<Long, AssetResponsibility> responsibilities = new ConcurrentHashMap<>();

    @Override
    public AssetResponsibility assign(long assetId, String responsibleUserId, String responsibilityScope) {
        var responsibility = new AssetResponsibility(assetId, responsibleUserId, responsibilityScope);
        responsibilities.put(assetId, responsibility);
        return responsibility;
    }

    @Override
    public Optional<AssetResponsibility> currentResponsibility(long assetId) {
        return Optional.ofNullable(responsibilities.get(assetId));
    }
}
