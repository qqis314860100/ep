package com.tianshu.assets.governance.infrastructure;

import com.tianshu.assets.asset.domain.AssetRepository;
import com.tianshu.assets.asset.domain.AssetSearchCriteria;
import com.tianshu.assets.asset.domain.AssetType;
import com.tianshu.assets.governance.standard.application.GovernanceStandardImpactPort;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;

public class RepositoryGovernanceStandardImpactAdapter implements GovernanceStandardImpactPort {

    private final ObjectProvider<AssetRepository> assetRepositories;

    public RepositoryGovernanceStandardImpactAdapter(ObjectProvider<AssetRepository> assetRepositories) {
        this.assetRepositories = assetRepositories;
    }

    @Override
    public List<Long> findPotentiallyAffectedAssetIds(List<AssetType> applicableAssetTypes) {
        var repository = assetRepositories.getIfAvailable();
        if (repository == null) return List.of();
        if (applicableAssetTypes == null || applicableAssetTypes.isEmpty()) {
            return assetIds(repository, null);
        }
        return applicableAssetTypes.stream()
                .flatMap(type -> assetIds(repository, type).stream())
                .distinct()
                .toList();
    }

    private List<Long> assetIds(AssetRepository repository, AssetType type) {
        return repository.search(new AssetSearchCriteria(
                        "", type, null, "", "", "", "", "", null, 1, 10_000))
                .items().stream().map(asset -> asset.id()).toList();
    }
}
