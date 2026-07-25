package com.tianshu.assets.dictionary.application;

import com.tianshu.assets.dictionary.domain.DictionaryCategory;
import com.tianshu.assets.dictionary.domain.DictionaryItem;
import com.tianshu.assets.dictionary.domain.DictionaryStatus;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class DictionaryService {

    private static final List<DictionaryCategory> CATEGORIES = List.of(
            new DictionaryCategory("PLATFORM_FAMILY", "平台族", "产品体系", null, "产品平台的上层业务分组", 10),
            new DictionaryCategory("PLATFORM_VARIANT", "平台子类", "产品体系", "PLATFORM_FAMILY", "八个标准平台原子值", 20),
            new DictionaryCategory("PRODUCT_LINE", "蓝本", "产品体系", "PLATFORM_VARIANT", "平台子类下可复用的标准产品及产线设计方案", 30),
            new DictionaryCategory("BASE", "基地", "生产体系", null, "资产实际应用的生产基地", 40),
            new DictionaryCategory("PRODUCTION_LINE", "拉线", "生产体系", "BASE", "基地内的生产拉线", 50),
            new DictionaryCategory("PROCESS_SECTION", "工序段", "生产体系", "PRODUCTION_LINE", "拉线内的工艺区段", 60),
            new DictionaryCategory("SPECIALTY", "专业类别", "资产分类", null, "机械、电气、液压等受控专业", 70),
            new DictionaryCategory("TAG", "资料标签", "资产分类", null, "用于补充描述资产特征", 80),
            new DictionaryCategory("MODULE_TAG", "模组标签", "资产分类", null, "模组及标准设备模块的受控标签", 90),
            new DictionaryCategory("ASSET_TYPE", "资产类型", "资产分类", null, "三维、二维或混合资产", 100),
            new DictionaryCategory("FILE_ROLE", "文件角色", "资产分类", null, "资产包内文件的业务角色", 110),
            new DictionaryCategory("RELATION_TYPE", "关系类型", "资产关系", null, "资产间包含、引用、配套或替代关系", 120));

    private final DictionaryStore store;

    public DictionaryService(DictionaryStore store) {
        this.store = store;
    }

    public List<DictionaryCategory> categories() {
        return CATEGORIES;
    }

    public List<DictionaryItem> list(String category, Long parentId, String query, DictionaryStatus status) {
        var normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return store.findAll().stream()
                .filter(item -> category == null || category.isBlank() || item.category().equals(category))
                .filter(item -> parentId == null || Objects.equals(item.parentId(), parentId))
                .filter(item -> status == null || item.status() == status)
                .filter(item -> normalizedQuery.isEmpty()
                        || item.name().toLowerCase(Locale.ROOT).contains(normalizedQuery)
                        || item.code().toLowerCase(Locale.ROOT).contains(normalizedQuery))
                .sorted(Comparator.comparingInt(DictionaryItem::sortOrder).thenComparing(DictionaryItem::name))
                .toList();
    }

    public DictionaryItem create(String category, String code, String name, Long parentId, int sortOrder,
            String description, String forwardName, String reverseName, boolean directional, boolean allowDuplicate) {
        var categoryDefinition = requireCategory(category);
        validateCodeUnique(category, code, 0);
        validateParent(categoryDefinition, parentId);
        validateRelationFields(category, name, forwardName, reverseName);
        var now = LocalDateTime.now();
        return store.create(new DictionaryItem(0, category, normalizeCode(code), name.trim(), parentId,
                DictionaryStatus.ENABLED, sortOrder, 0, 0, trimToNull(description), trimToNull(forwardName),
                trimToNull(reverseName), directional, allowDuplicate, null, now));
    }

    public DictionaryItem update(long id, long version, String code, String name, Long parentId,
            DictionaryStatus status, int sortOrder, String description, String forwardName, String reverseName,
            boolean directional, boolean allowDuplicate) {
        var current = requireItem(id);
        if (current.status() == DictionaryStatus.MERGED) {
            throw new DictionaryConflictException("已合并字典项不能继续编辑");
        }
        var category = requireCategory(current.category());
        validateCodeUnique(current.category(), code, id);
        validateParent(category, parentId);
        validateRelationFields(current.category(), name, forwardName, reverseName);
        if (status == DictionaryStatus.MERGED) {
            throw new IllegalArgumentException("请使用合并操作设置合并状态");
        }
        if (status == DictionaryStatus.DISABLED && hasEnabledChildren(id)) {
            throw new DictionaryConflictException("请先停用该字典项下的启用子项");
        }
        return store.update(new DictionaryItem(id, current.category(), normalizeCode(code), name.trim(), parentId,
                status, sortOrder, current.usageCount(), current.version(), trimToNull(description),
                trimToNull(forwardName), trimToNull(reverseName), directional, allowDuplicate,
                current.mergeTargetId(), LocalDateTime.now()), version);
    }

    public DictionaryItem merge(long sourceId, long targetId, long version) {
        if (sourceId == targetId) throw new IllegalArgumentException("不能合并到当前字典项自身");
        var source = requireItem(sourceId);
        var target = requireItem(targetId);
        if (!source.category().equals(target.category())) {
            throw new IllegalArgumentException("只能合并同一分类下的字典项");
        }
        if (target.status() != DictionaryStatus.ENABLED) {
            throw new DictionaryConflictException("合并目标必须处于启用状态");
        }
        if (hasEnabledChildren(sourceId)) {
            throw new DictionaryConflictException("存在启用子项，不能直接合并");
        }
        return store.update(new DictionaryItem(source.id(), source.category(), source.code(), source.name(),
                source.parentId(), DictionaryStatus.MERGED, source.sortOrder(), source.usageCount(), source.version(),
                source.description(), source.forwardName(), source.reverseName(), source.directional(),
                source.allowDuplicate(), targetId, LocalDateTime.now()), version);
    }

    private DictionaryCategory requireCategory(String code) {
        return CATEGORIES.stream().filter(category -> category.code().equals(code)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知字典分类：" + code));
    }

    private DictionaryItem requireItem(long id) {
        return store.findById(id).orElseThrow(() -> new DictionaryNotFoundException(id));
    }

    private void validateCodeUnique(String category, String code, long excludedId) {
        var normalized = normalizeCode(code);
        if (store.findAll().stream().anyMatch(item -> item.id() != excludedId
                && item.category().equals(category) && item.code().equalsIgnoreCase(normalized))) {
            throw new DictionaryConflictException("同一分类下的字典编码不能重复");
        }
    }

    private void validateParent(DictionaryCategory category, Long parentId) {
        if (category.parentCategory() == null && parentId != null) {
            throw new IllegalArgumentException("该分类不允许设置上级字典项");
        }
        if (category.parentCategory() != null) {
            if (parentId == null) throw new IllegalArgumentException("请选择上级字典项");
            var parent = requireItem(parentId);
            if (!parent.category().equals(category.parentCategory()) || parent.status() != DictionaryStatus.ENABLED) {
                throw new IllegalArgumentException("上级字典项分类或状态不正确");
            }
        }
    }

    private void validateRelationFields(String category, String name, String forwardName, String reverseName) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("字典名称不能为空");
        if ("RELATION_TYPE".equals(category)
                && (forwardName == null || forwardName.isBlank() || reverseName == null || reverseName.isBlank())) {
            throw new IllegalArgumentException("关系类型必须填写正向名称和反向名称");
        }
    }

    private boolean hasEnabledChildren(long id) {
        return store.findAll().stream().anyMatch(item -> Objects.equals(item.parentId(), id)
                && item.status() == DictionaryStatus.ENABLED);
    }

    private String normalizeCode(String code) {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("字典编码不能为空");
        return code.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
