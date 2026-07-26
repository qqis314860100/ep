package com.tianshu.assets.governance.execution.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tianshu.assets.asset.domain.AssetScope;
import com.tianshu.assets.dictionary.application.DictionaryStore;
import com.tianshu.assets.dictionary.domain.DictionaryStatus;
import com.tianshu.assets.governance.application.GovernanceConflictException;
import com.tianshu.assets.governance.application.GovernanceValidationException;
import com.tianshu.assets.governance.application.GovernanceVersionConflictException;
import com.tianshu.assets.governance.issue.domain.GovernanceField;
import com.tianshu.assets.governance.task.application.GovernanceEmployeeDirectory;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.StreamSupport;
import org.springframework.stereotype.Component;

@Component
public class FieldSupplementActionHandler implements GovernanceActionHandler {

    private static final Set<String> DESCRIPTION_FIELDS = Set.of("description");
    private static final Set<String> SPECIALTY_FIELDS = Set.of("specialtyItemIds");
    private static final Set<String> OWNER_FIELDS = Set.of("ownerUserId", "ownerName");
    private static final Set<String> SCOPE_ROOT_FIELDS = Set.of("scopes");
    private static final Set<String> SCOPE_FIELDS = Set.of(
            "platformFamily", "platformVariant", "productLine", "base", "productionLine", "processSection");
    private static final Set<String> REQUIRED_SCOPE_FIELDS = Set.of(
            "platformFamily", "platformVariant", "productLine", "base", "productionLine");

    private final ObjectMapper objectMapper;
    private final DictionaryStore dictionaryStore;
    private final GovernanceEmployeeDirectory employeeDirectory;

    public FieldSupplementActionHandler(
            ObjectMapper objectMapper,
            DictionaryStore dictionaryStore,
            GovernanceEmployeeDirectory employeeDirectory) {
        this.objectMapper = objectMapper;
        this.dictionaryStore = dictionaryStore;
        this.employeeDirectory = employeeDirectory;
    }

    @Override
    public void validate(GovernanceField field, String proposedValueJson, ValidationContext context) {
        var root = parseObject(proposedValueJson);
        switch (field) {
            case DESCRIPTION -> validateDescription(root);
            case SPECIALTIES -> validateSpecialties(root, context);
            case OWNER -> validateOwner(root);
            case SCOPE -> validateScopes(root, context);
        }
    }

    private ObjectNode parseObject(String json) {
        try {
            var node = objectMapper.readTree(json);
            if (!(node instanceof ObjectNode objectNode)) throw invalidStructure();
            return objectNode;
        } catch (JsonProcessingException exception) {
            throw new GovernanceValidationException("治理结果 JSON 格式不正确");
        }
    }

    private void validateDescription(ObjectNode root) {
        requireExactFields(root, DESCRIPTION_FIELDS);
        var description = root.get("description");
        if (description == null || !description.isTextual() || description.asText().isBlank()) {
            throw new GovernanceValidationException("说明不能为空");
        }
    }

    private void validateSpecialties(ObjectNode root, ValidationContext context) {
        requireExactFields(root, SPECIALTY_FIELDS);
        var ids = root.get("specialtyItemIds");
        if (ids == null || !ids.isArray() || ids.isEmpty()
                || StreamSupport.stream(ids.spliterator(), false)
                        .anyMatch(node -> !node.isIntegralNumber() || !node.canConvertToLong())) {
            throw invalidStructure();
        }
        var frozenVersion = context.frozenRules().dictionaryVersions().get("specialty");
        var enabledVersion = context.enabledRules().dictionaryVersions().get("specialty");
        if (frozenVersion == null || !frozenVersion.equals(enabledVersion)) {
            throw new GovernanceConflictException("专业字典版本已变化，请刷新后重试");
        }
        var uniqueIds = new HashSet<Long>();
        for (var idNode : ids) {
            if (!uniqueIds.add(idNode.longValue())) throw new GovernanceValidationException("专业类别不能重复");
            var item = dictionaryStore.findById(idNode.longValue()).orElse(null);
            if (item == null || !"SPECIALTY".equals(item.category()) || item.status() != DictionaryStatus.ENABLED) {
                throw new GovernanceValidationException("专业类别必须选择启用的字典项");
            }
        }
    }

    private void validateOwner(ObjectNode root) {
        requireExactFields(root, OWNER_FIELDS);
        var userId = text(root, "ownerUserId");
        var name = text(root, "ownerName");
        var valid = employeeDirectory.findAllEmployees().stream()
                .anyMatch(employee -> employee.id().equals(userId) && employee.name().equals(name));
        if (!valid) throw new GovernanceValidationException("负责人必须来自员工目录");
    }

    private void validateScopes(ObjectNode root, ValidationContext context) {
        var frozenVersion = context.frozenRules().dictionaryVersions().get("scope");
        var enabledVersion = context.enabledRules().dictionaryVersions().get("scope");
        if (frozenVersion == null || !frozenVersion.equals(enabledVersion)) {
            throw new GovernanceVersionConflictException("适用范围字典版本已变化，请刷新后重试");
        }
        requireExactFields(root, SCOPE_ROOT_FIELDS);
        var scopes = root.get("scopes");
        if (scopes == null || !scopes.isArray() || scopes.isEmpty()) throw invalidStructure();
        for (var node : scopes) {
            if (!(node instanceof ObjectNode scopeNode)) throw invalidStructure();
            var fields = fieldNames(scopeNode);
            if (!SCOPE_FIELDS.containsAll(fields)
                    || fields.stream().anyMatch(field -> !scopeNode.get(field).isTextual())) {
                throw invalidStructure();
            }
            if (!fields.equals(SCOPE_FIELDS)
                    || REQUIRED_SCOPE_FIELDS.stream().anyMatch(field -> text(scopeNode, field).isBlank())) {
                throw new GovernanceValidationException("适用范围层级必须完整");
            }
            var proposed = new AssetScope(
                    text(scopeNode, "platformFamily"), text(scopeNode, "productLine"),
                    text(scopeNode, "base"), text(scopeNode, "productionLine"),
                    text(scopeNode, "processSection"), text(scopeNode, "platformFamily"),
                    text(scopeNode, "platformVariant"));
            if (context.validScopes().stream().noneMatch(scope -> sameScope(scope, proposed))) {
                throw new GovernanceValidationException("产品与生产条件必须来自同一个适用范围");
            }
        }
    }

    private boolean sameScope(AssetScope left, AssetScope right) {
        return left.platformFamily().equals(right.platformFamily())
                && left.platformVariant().equals(right.platformVariant())
                && left.productLine().equals(right.productLine())
                && left.base().equals(right.base())
                && left.productionLine().equals(right.productionLine())
                && left.processSection().equals(right.processSection());
    }

    private void requireExactFields(ObjectNode node, Set<String> expected) {
        if (!fieldNames(node).equals(expected)) throw invalidStructure();
    }

    private Set<String> fieldNames(ObjectNode node) {
        var names = new HashSet<String>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private String text(ObjectNode node, String field) {
        var value = node.get(field);
        return value != null && value.isTextual() ? value.asText().trim() : "";
    }

    private GovernanceValidationException invalidStructure() {
        return new GovernanceValidationException("治理结果结构不正确");
    }
}
