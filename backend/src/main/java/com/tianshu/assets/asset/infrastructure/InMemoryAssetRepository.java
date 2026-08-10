package com.tianshu.assets.asset.infrastructure;

import com.tianshu.assets.asset.domain.Asset;
import com.tianshu.assets.asset.domain.AssetFile;
import com.tianshu.assets.asset.domain.AssetPage;
import com.tianshu.assets.asset.domain.AssetRelation;
import com.tianshu.assets.asset.domain.AssetRepository;
import com.tianshu.assets.asset.domain.AssetScope;
import com.tianshu.assets.asset.domain.AssetSearchCriteria;
import com.tianshu.assets.asset.domain.AssetStatus;
import com.tianshu.assets.asset.domain.AssetType;
import com.tianshu.assets.asset.domain.RelationType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("dev")
public class InMemoryAssetRepository implements AssetRepository {

    private final List<Asset> assets = new ArrayList<>(List.of(
            asset(
                    101,
                    "DM-ND-A-0001",
                    "焊接工位总成数模",
                    "宁德基地 A 拉线焊接工位设备总成和工装空间模型。",
                    AssetType.MIXED_ASSET,
                    AssetStatus.STANDARDIZED,
                    List.of("机械", "工装"),
                    List.of(
                            new AssetScope("乘用车", "H03", "宁德基地", "A 拉线", "焊接段", "乘用车", "大面水冷"),
                            new AssetScope("乘用车", "H03", "溧阳基地", "B 拉线", "焊接段", "乘用车", "大面水冷")),
                    List.of(
                            new AssetFile(1001, "welding-station.x_t", "X_T", 428_500_000, "三维源模型", false, true),
                            new AssetFile(1002, "welding-layout.pdf", "PDF", 12_800_000, "二维图纸", true, false),
                            new AssetFile(1003, "preview.png", "PNG", 2_400_000, "预览文件", true, false)),
                    "陈工",
                    "设备工程部",
                    false),
            asset(
                    102,
                    "DM-ND-A-0002",
                    "定位工装数模",
                    "焊接模块使用的定位工装三维模型。",
                    AssetType.THREE_DIMENSIONAL_MODEL,
                    AssetStatus.STANDARDIZED,
                    List.of("机械", "工装"),
                    List.of(new AssetScope("乘用车", "H03", "宁德基地", "A 拉线", "焊接段", "乘用车", "大面水冷")),
                    List.of(new AssetFile(1010, "fixture.step", "STEP", 86_000_000, "三维源模型", false, true)),
                    "李工",
                    "工艺仿真组",
                    false),
            asset(
                    103,
                    "DM-ND-A-0003",
                    "输送模块布置数模",
                    "焊接工位前后输送模块布置和接口空间。",
                    AssetType.MIXED_ASSET,
                    AssetStatus.PENDING_CURATION,
                    List.of("机械"),
                    List.of(new AssetScope("乘用车", "H03", "宁德基地", "A 拉线", "焊接段", "乘用车", "底部水冷")),
                    List.of(
                            new AssetFile(1020, "conveyor.stp", "STP", 124_000_000, "三维源模型", false, true),
                            new AssetFile(1021, "conveyor.jpg", "JPG", 1_800_000, "预览文件", true, false)),
                    "王工",
                    "设备工程部",
                    false),
            asset(
                    104,
                    "LEGACY-00000104",
                    "XM-PL01 设备图",
                    "历史资料，基地和标准范围待补充。",
                    AssetType.TWO_DIMENSIONAL_DRAWING,
                    AssetStatus.PENDING_CURATION,
                    List.of("机械"),
                    List.of(new AssetScope("H03底部水冷", "", "", "XM-PL01", "", "乘用车", "底部水冷")),
                    List.of(new AssetFile(1030, "legacy-layout.pdf", "PDF", 8_400_000, "二维图纸", true, true)),
                    "赵工",
                    "设备工程部",
                    true),
            asset(
                    105,
                    "DM-LY-B-0012",
                    "PACK 段设备接口图",
                    "溧阳基地 B 拉线 PACK 段设备接口二维图纸。",
                    AssetType.TWO_DIMENSIONAL_DRAWING,
                    AssetStatus.STANDARDIZED,
                    List.of("机械", "电气"),
                    List.of(new AssetScope("商用车", "P02", "溧阳基地", "B 拉线", "PACK 段", "商用车", "")),
                    List.of(new AssetFile(1040, "pack-interface.pdf", "PDF", 6_200_000, "二维图纸", true, true)),
                    "周工",
                    "自动化部",
                    false)));

    private final AtomicLong nextAssetId = new AtomicLong(106);
    private final AtomicLong nextFileId = new AtomicLong(1050);

    private final List<AssetRelation> relations = List.of(
            new AssetRelation(
                    1,
                    101,
                    102,
                    "DM-ND-A-0002",
                    "定位工装数模",
                    AssetType.THREE_DIMENSIONAL_MODEL,
                    AssetStatus.STANDARDIZED,
                    RelationType.REFERENCES,
                    "引用",
                    "宁德基地 / A 拉线 / 焊接段",
                    "焊接总成引用该定位工装。"),
            new AssetRelation(
                    2,
                    101,
                    103,
                    "DM-ND-A-0003",
                    "输送模块布置数模",
                    AssetType.MIXED_ASSET,
                    AssetStatus.PENDING_CURATION,
                    RelationType.CONTAINS,
                    "包含",
                    "宁德基地 / A 拉线 / 焊接段",
                    "整线总成包含输送模块。"));

    @Override
    public AssetPage search(AssetSearchCriteria criteria) {
        var filtered = assets.stream().filter(asset -> matches(asset, criteria)).toList();
        var offset = (long) (criteria.page() - 1) * criteria.perPage();
        var pageItems = filtered.stream().skip(offset).limit(criteria.perPage()).toList();
        return new AssetPage(pageItems, filtered.size(), criteria.page(), criteria.perPage());
    }

    @Override
    public Optional<Asset> findById(long id) {
        return assets.stream().filter(asset -> asset.id() == id).findFirst();
    }

    @Override
    public List<AssetRelation> findRelations(long assetId) {
        return relations.stream().filter(relation -> relation.sourceAssetId() == assetId).toList();
    }

    @Override
    public synchronized Asset save(Asset asset) {
        var files = asset.files().stream()
                .map(file -> file.id() == 0
                        ? new AssetFile(nextFileId.getAndIncrement(), file.name(), file.format(), file.sizeBytes(),
                                file.role(), file.previewable(), file.primary(), file.storageKey(), file.contentSha256())
                        : file)
                .toList();
        var saved = copy(asset, nextAssetId.getAndIncrement(), files);
        assets.add(saved);
        return saved;
    }

    @Override
    public synchronized Asset update(Asset asset) {
        var index = -1;
        for (var i = 0; i < assets.size(); i++) {
            if (assets.get(i).id() == asset.id()) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            throw new IllegalArgumentException("资产不存在：" + asset.id());
        }
        assets.set(index, asset);
        return asset;
    }

    @Override
    public boolean existsByAssetNumber(String assetNumber) {
        return assetNumber != null && !assetNumber.isBlank()
                && assets.stream().anyMatch(asset -> assetNumber.equalsIgnoreCase(asset.assetNumber()));
    }

    private boolean matches(Asset asset, AssetSearchCriteria criteria) {
        var normalizedQuery = criteria.query().toLowerCase(Locale.ROOT);
        var matchesQuery = normalizedQuery.isBlank()
                || asset.assetNumber().toLowerCase(Locale.ROOT).contains(normalizedQuery)
                || asset.name().toLowerCase(Locale.ROOT).contains(normalizedQuery)
                || asset.description().toLowerCase(Locale.ROOT).contains(normalizedQuery)
                || asset.files().stream()
                        .anyMatch(file -> file.name().toLowerCase(Locale.ROOT).contains(normalizedQuery));
        var matchesType = criteria.assetType() == null || asset.assetType() == criteria.assetType();
        var matchesStatus = criteria.status() == null || asset.status() == criteria.status();
        var matchesOwner = criteria.ownerName() == null || criteria.ownerName().isBlank()
                || criteria.ownerName().equals(asset.ownerName());
        var matchesPreviewable = criteria.previewable() == null
                || !criteria.previewable()
                || asset.files().stream().anyMatch(AssetFile::previewable);
        var matchesScope = asset.scopes().stream().anyMatch(scope ->
                (criteria.platformFamily() == null || criteria.platformFamily().isBlank()
                        || criteria.platformFamily().equals(scope.platformFamily()))
                        && (criteria.platformVariant() == null || criteria.platformVariant().isBlank()
                        || criteria.platformVariant().equals(scope.platformVariant()))
                        &&
                (criteria.base() == null || criteria.base().isBlank() || criteria.base().equals(scope.base()))
                        && (criteria.productionLine() == null
                                || criteria.productionLine().isBlank()
                                || criteria.productionLine().equals(scope.productionLine()))
                        && (criteria.productLine().isBlank() || criteria.productLine().equals(scope.productLine()))
                        && (criteria.processSection().isBlank() || criteria.processSection().equals(scope.processSection())));
        return matchesQuery && matchesType && matchesStatus && matchesOwner && matchesPreviewable && matchesScope;
    }

    private static Asset asset(
            long id,
            String number,
            String name,
            String description,
            AssetType type,
            AssetStatus status,
            List<String> specialties,
            List<AssetScope> scopes,
            List<AssetFile> files,
            String owner,
            String department,
            boolean legacy) {
        return new Asset(
                id,
                number,
                name,
                description,
                type,
                status,
                specialties,
                List.of("设备数模"),
                id == 101 ? List.of("标准设备模块") : List.of(),
                id == 101 || id == 102,
                id == 101 ? List.of(102L, 103L) : List.of(),
                id <= 103 ? "EQ-ND-A-00" + (id - 100) : "",
                scopes,
                files,
                owner,
                department,
                Instant.parse("2026-07-18T02:00:00Z").minusSeconds(id * 3600),
                legacy);
    }

    private static Asset copy(Asset asset, long id, List<AssetFile> files) {
        return new Asset(
                id,
                asset.assetNumber(),
                asset.name(),
                asset.description(),
                asset.assetType(),
                asset.status(),
                asset.specialties(),
                asset.tags(),
                asset.scopes(),
                files,
                asset.ownerName(),
                asset.ownerDepartment(),
                asset.updatedAt(),
                asset.legacy());
    }
}
