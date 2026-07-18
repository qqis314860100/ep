package com.tianshu.assets.asset.application;

import com.tianshu.assets.asset.domain.Asset;
import com.tianshu.assets.asset.domain.AssetPage;
import com.tianshu.assets.asset.domain.AssetRelation;
import com.tianshu.assets.asset.domain.AssetRepository;
import com.tianshu.assets.asset.domain.AssetSearchCriteria;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AssetQueryService {

    private final AssetRepository assetRepository;

    public AssetQueryService(AssetRepository assetRepository) {
        this.assetRepository = assetRepository;
    }

    public AssetPage search(AssetSearchCriteria criteria) {
        return assetRepository.search(criteria);
    }

    public Asset get(long id) {
        return assetRepository.findById(id).orElseThrow(() -> new AssetNotFoundException(id));
    }

    public java.util.Optional<Asset> getOptional(long id) {
        return assetRepository.findById(id);
    }

    public List<AssetRelation> getRelations(long id) {
        get(id);
        return assetRepository.findRelations(id);
    }
}
