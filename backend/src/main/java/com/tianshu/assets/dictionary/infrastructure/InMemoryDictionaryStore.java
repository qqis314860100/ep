package com.tianshu.assets.dictionary.infrastructure;

import com.tianshu.assets.dictionary.application.DictionaryConflictException;
import com.tianshu.assets.dictionary.application.DictionaryStore;
import com.tianshu.assets.dictionary.domain.DictionaryItem;
import com.tianshu.assets.dictionary.domain.DictionaryStatus;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("dev")
public class InMemoryDictionaryStore implements DictionaryStore {

    private final Map<Long, DictionaryItem> items = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong(1000);

    public InMemoryDictionaryStore() {
        seedProductHierarchy();
        seedProductionHierarchy();
        seedAssetClassifications();
        seedDocumentCategories();
    }

    @Override
    public List<DictionaryItem> findAll() {
        return new ArrayList<>(items.values());
    }

    @Override
    public Optional<DictionaryItem> findById(long id) {
        return Optional.ofNullable(items.get(id));
    }

    @Override
    public DictionaryItem create(DictionaryItem item) {
        var created = copy(item, nextId.getAndIncrement(), 0, LocalDateTime.now());
        items.put(created.id(), created);
        return created;
    }

    @Override
    public DictionaryItem update(DictionaryItem item, long expectedVersion) {
        return items.compute(item.id(), (id, current) -> {
            if (current == null) throw new DictionaryConflictException("字典项已不存在");
            if (current.version() != expectedVersion) {
                throw new DictionaryConflictException("字典项已被其他用户更新，请刷新后重试");
            }
            return copy(item, id, expectedVersion + 1, LocalDateTime.now());
        });
    }

    private DictionaryItem copy(DictionaryItem source, long id, long version, LocalDateTime updatedAt) {
        return new DictionaryItem(id, source.category(), source.code(), source.name(), source.parentId(),
                source.status(), source.sortOrder(), source.usageCount(), version, source.description(),
                source.forwardName(), source.reverseName(), source.directional(), source.allowDuplicate(),
                source.mergeTargetId(), updatedAt);
    }

    private void seedProductHierarchy() {
        seed(1, "PLATFORM_FAMILY", "PASSENGER", "乘用车", null, 10, 5);
        seed(2, "PLATFORM_FAMILY", "COMMERCIAL", "商用车", null, 20, 1);
        seed(3, "PLATFORM_FAMILY", "ENERGY_STORAGE", "储能", null, 30, 0);
        seed(4, "PLATFORM_FAMILY", "MODULE", "模组", null, 40, 0);
        seed(5, "PLATFORM_FAMILY", "CYLINDRICAL", "圆柱", null, 50, 0);
        seed(11, "PLATFORM_VARIANT", "PASSENGER_SURFACE_COOLING", "大面水冷", 1L, 10, 3);
        seed(12, "PLATFORM_VARIANT", "PASSENGER_BOTTOM_COOLING", "底部水冷", 1L, 20, 2);
        seed(13, "PLATFORM_VARIANT", "COMMERCIAL", "商用车", 2L, 10, 1);
        seed(14, "PLATFORM_VARIANT", "STORAGE_CONTAINER", "集装箱", 3L, 10, 0);
        seed(15, "PLATFORM_VARIANT", "STORAGE_BOX", "电箱", 3L, 20, 0);
        seed(16, "PLATFORM_VARIANT", "STORAGE_CABINET", "电柜", 3L, 30, 0);
        seed(17, "PLATFORM_VARIANT", "MODULE", "模组", 4L, 10, 0);
        seed(18, "PLATFORM_VARIANT", "CYLINDRICAL", "圆柱", 5L, 10, 0);
        seed(21, "PRODUCT_LINE", "H03_BOTTOM", "H03", 12L, 10, 2);
        seed(22, "PRODUCT_LINE", "P02", "P02", 13L, 10, 1);
        seed(23, "PRODUCT_LINE", "H03_SURFACE", "H03", 11L, 10, 3);
        seed(24, "PRODUCT_LINE", "M01", "M01", 17L, 10, 0);
        seed(25, "PRODUCT_LINE", "P01", "P01", 18L, 10, 0);
    }

    private void seedProductionHierarchy() {
        seed(101, "BASE", "NINGDE", "宁德基地", null, 10, 4);
        seed(102, "BASE", "LIYANG", "溧阳基地", null, 20, 2);
        seed(111, "PRODUCTION_LINE", "NINGDE_A", "A 拉线", 101L, 10, 4);
        seed(112, "PRODUCTION_LINE", "LIYANG_B", "B 拉线", 102L, 10, 2);
        seed(121, "PROCESS_SECTION", "WELDING", "焊接段", 111L, 10, 3);
        seed(122, "PROCESS_SECTION", "PACK", "PACK 段", 112L, 10, 1);
    }

    private void seedAssetClassifications() {
        seed(201, "SPECIALTY", "MECHANICAL", "机械", null, 10, 5);
        seed(202, "SPECIALTY", "ELECTRICAL", "电气", null, 20, 1);
        seed(203, "SPECIALTY", "HYDRAULIC", "液压", null, 30, 0);
        seed(204, "SPECIALTY", "PNEUMATIC", "气动", null, 40, 0);
        seed(205, "SPECIALTY", "TOOLING", "工装", null, 50, 3);
        seed(211, "TAG", "EQUIPMENT_MODEL", "设备数模", null, 10, 2);
        seed(212, "TAG", "WELDING", "焊接", null, 20, 1);
        seed(213, "TAG", "FIXTURE", "工装", null, 30, 1);
        seed(221, "MODULE_TAG", "STANDARD_MODULE", "标准设备模块", null, 10, 2);
        seed(222, "MODULE_TAG", "POSITIONING_MODULE", "定位模块", null, 20, 1);
        seed(223, "MODULE_TAG", "CONVEYOR_MODULE", "输送模块", null, 30, 1);
        seed(231, "ASSET_TYPE", "THREE_DIMENSIONAL_MODEL", "三维模型", null, 10, 2);
        seed(232, "ASSET_TYPE", "TWO_DIMENSIONAL_DRAWING", "二维图纸", null, 20, 2);
        seed(233, "ASSET_TYPE", "MIXED_ASSET", "混合资产", null, 30, 2);
        seed(234, "ASSET_TYPE", "OTHER", "其他资料", null, 40, 0);
        seed(241, "FILE_ROLE", "THREE_DIMENSIONAL_SOURCE", "三维源模型", null, 10, 3);
        seed(242, "FILE_ROLE", "TWO_DIMENSIONAL_DRAWING", "二维图纸", null, 20, 3);
        seed(243, "FILE_ROLE", "PREVIEW_FILE", "预览文件", null, 30, 2);
        seed(244, "FILE_ROLE", "INSTRUCTION", "说明附件", null, 40, 0);
        seed(245, "FILE_ROLE", "OTHER_ATTACHMENT", "其他附件", null, 50, 0);
        seedRelation(251, "CONTAINS", "包含", "包含", "属于", true, false, 2);
        seedRelation(252, "REFERENCES", "引用", "引用", "被引用", true, true, 3);
        seedRelation(253, "MATCHES", "配套", "配套", "配套", false, true, 1);
        seedRelation(254, "REPLACES", "替代", "替代", "被替代", true, false, 0);
    }

    private void seedDocumentCategories() {
        seed(261, "DOCUMENT_CATEGORY", "TECHNICAL_SPECIFICATION", "技术规范", null, 10, 0);
        seed(262, "DOCUMENT_CATEGORY", "MANUAL", "说明书", null, 20, 0);
        seed(263, "DOCUMENT_CATEGORY", "WORK_INSTRUCTION", "作业指导书", null, 30, 0);
        seed(264, "DOCUMENT_CATEGORY", "COMMISSIONING", "调试资料", null, 40, 0);
        seed(265, "DOCUMENT_CATEGORY", "ACCEPTANCE", "验收资料", null, 50, 0);
        seed(266, "DOCUMENT_CATEGORY", "STANDARD_TEMPLATE", "标准模板", null, 60, 0);
    }

    private void seed(long id, String category, String code, String name, Long parentId, int sortOrder, long usageCount) {
        items.put(id, new DictionaryItem(id, category, code, name, parentId, DictionaryStatus.ENABLED,
                sortOrder, usageCount, 0, null, null, null, false, false, null, LocalDateTime.now()));
    }

    private void seedRelation(long id, String code, String name, String forward, String reverse,
            boolean directional, boolean allowDuplicate, long usageCount) {
        items.put(id, new DictionaryItem(id, "RELATION_TYPE", code, name, null, DictionaryStatus.ENABLED,
                (int) (id - 250) * 10, usageCount, 0, null, forward, reverse, directional, allowDuplicate,
                null, LocalDateTime.now()));
    }
}
