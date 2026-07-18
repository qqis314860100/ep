package com.tianshu.assets.asset.domain;

import java.util.List;
import java.util.Optional;

public interface AssetRepository {

    AssetPage search(AssetSearchCriteria criteria);

    Optional<Asset> findById(long id);

    List<AssetRelation> findRelations(long assetId);

    Asset save(Asset asset);

    Asset update(Asset asset);

    boolean existsByAssetNumber(String assetNumber);
}
