package com.tianshu.assets.asset.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tianshu.assets.asset.domain.Asset;
import com.tianshu.assets.asset.domain.AssetRelation;
import com.tianshu.assets.asset.domain.AssetRepository;
import com.tianshu.assets.asset.domain.AssetScope;
import com.tianshu.assets.asset.domain.AssetStatus;
import com.tianshu.assets.asset.domain.AssetType;
import com.tianshu.assets.asset.domain.RelationType;
import com.tianshu.assets.system.domain.OperationLog;
import com.tianshu.assets.system.domain.OperationLogStore;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 资产关系写入与规则校验（RELATION-03/04/05）：
 * 不自环、不重复（配套关系按无方向对称去重）、包含关系无循环、解除不删资产，
 * 每次变更记录操作人、时间、原值与新值。
 */
@Service
public class AssetRelationService {

    private static final Map<RelationType, String[]> DIRECTION_LABELS = Map.of(
            RelationType.CONTAINS, new String[] { "包含", "属于" },
            RelationType.REFERENCES, new String[] { "引用", "被引用" },
            RelationType.MATCHES, new String[] { "配套", "配套" },
            RelationType.REPLACES, new String[] { "替代", "被替代" },
            RelationType.ASSOCIATED_WITH, new String[] { "关联", "关联" });

    private final AssetRepository assets;
    private final OperationLogStore operationLogs;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AssetRelationService(AssetRepository assets, OperationLogStore operationLogs) {
        this.assets = assets;
        this.operationLogs = operationLogs;
    }

    public List<AssetRelation> findRelations(long assetId) {
        return assets.findRelations(assetId).stream()
                .map(relation -> relation.fromSourceSide(assetId)
                        ? relation
                        : withDirectionLabel(relation, reverseLabel(relation.relationType())))
                .toList();
    }

    @Transactional
    public AssetRelation create(long sourceAssetId, long targetAssetId, RelationType relationType,
            String description, String operatorUserId, String operatorName) {
        var now = Instant.now();
        var target = requireAsset(targetAssetId);
        requireAsset(sourceAssetId);
        if (sourceAssetId == targetAssetId) {
            throw new AssetRelationConflictException("资产不能与自身建立关系");
        }
        rejectDuplicate(sourceAssetId, targetAssetId, relationType);
        if (relationType == RelationType.CONTAINS) {
            rejectContainsCycle(sourceAssetId, targetAssetId);
        }
        var created = assets.createRelation(view(0, sourceAssetId, target, relationType,
                description, operatorUserId, operatorName, now, 0));
        audit("RELATION_CREATE", created.id(), null, created, operatorUserId, now);
        return created;
    }

    @Transactional
    public AssetRelation update(long relationId, long sourceAssetId, long targetAssetId,
            RelationType relationType, String description, long expectedVersion,
            String operatorUserId, String operatorName) {
        var current = requireRelation(relationId);
        var now = Instant.now();
        var target = requireAsset(targetAssetId);
        requireAsset(sourceAssetId);
        if (sourceAssetId == targetAssetId) {
            throw new AssetRelationConflictException("资产不能与自身建立关系");
        }
        rejectDuplicateExcluding(sourceAssetId, targetAssetId, relationType, relationId);
        if (relationType == RelationType.CONTAINS) {
            rejectContainsCycleExcluding(sourceAssetId, targetAssetId, relationId);
        }
        var updated = assets.updateRelation(view(relationId, sourceAssetId, target, relationType,
                description, operatorUserId, operatorName, now, expectedVersion + 1), expectedVersion);
        audit("RELATION_UPDATE", relationId, current, updated, operatorUserId, now);
        return updated;
    }

    @Transactional
    public void remove(long relationId, String operatorUserId) {
        var current = requireRelation(relationId);
        assets.removeRelation(relationId);
        audit("RELATION_REMOVE", relationId, current, null, operatorUserId, Instant.now());
    }

    /** 以当前资产为中心的多层关系图（RELATION-02），按层展开、环安全、停用节点带状态标记。 */
    public RelationGraph relationGraph(long rootAssetId, int depth) {
        var boundedDepth = Math.min(Math.max(depth, 1), 3);
        var nodes = new LinkedHashMap<Long, GraphNode>();
        var edges = new LinkedHashMap<Long, GraphEdge>();
        var visited = new HashSet<Long>();
        nodes.put(rootAssetId, graphNode(requireAsset(rootAssetId), 0));
        visited.add(rootAssetId);
        var level = List.of(rootAssetId);
        for (var currentDepth = 1; currentDepth <= boundedDepth && !level.isEmpty(); currentDepth++) {
            var next = new ArrayList<Long>();
            for (var assetId : level) {
                for (var relation : assets.findRelations(assetId)) {
                    var otherId = relation.fromSourceSide(assetId)
                            ? relation.targetAssetId() : relation.sourceAssetId();
                    edges.putIfAbsent(relation.id(), new GraphEdge(relation.id(),
                            relation.sourceAssetId(), relation.targetAssetId(), relation.relationType(),
                            labels(relation.relationType())[0], relation.description()));
                    if (!visited.add(otherId)) continue;
                    nodes.put(otherId, graphNode(requireAsset(otherId), currentDepth));
                    next.add(otherId);
                }
            }
            level = next;
        }
        return new RelationGraph(List.copyOf(nodes.values()), List.copyOf(edges.values()));
    }

    private GraphNode graphNode(Asset asset, int depth) {
        return new GraphNode(asset.id(), asset.assetNumber(), asset.name(), asset.assetType(),
                asset.status(), depth);
    }

    public record RelationGraph(List<GraphNode> nodes, List<GraphEdge> edges) {}

    public record GraphNode(long assetId, String assetNumber, String assetName, AssetType assetType,
            AssetStatus status, int depth) {}

    public record GraphEdge(long id, long sourceAssetId, long targetAssetId, RelationType relationType,
            String directionLabel, String description) {}

    private AssetRelation view(long id, long sourceAssetId, Asset target, RelationType relationType,
            String description, String operatorUserId, String operatorName, Instant now, long version) {
        var labels = labels(relationType);
        return new AssetRelation(id, sourceAssetId, target.id(), target.assetNumber(), target.name(),
                target.assetType(), target.status(), relationType, labels[0], primaryScope(target),
                trimToNull(description), operatorUserId, now, operatorUserId, now, version);
    }

    private void rejectDuplicate(long sourceAssetId, long targetAssetId, RelationType relationType) {
        var duplicated = assets.findAllRelations().stream().anyMatch(relation ->
                relation.relationType() == relationType && samePair(relation, sourceAssetId, targetAssetId, relationType));
        if (duplicated) throw new AssetRelationConflictException("相同资产与关系类型的关系已存在");
    }

    private void rejectDuplicateExcluding(long sourceAssetId, long targetAssetId,
            RelationType relationType, long excludedId) {
        var duplicated = assets.findAllRelations().stream().anyMatch(relation ->
                relation.id() != excludedId && relation.relationType() == relationType
                        && samePair(relation, sourceAssetId, targetAssetId, relationType));
        if (duplicated) throw new AssetRelationConflictException("相同资产与关系类型的关系已存在");
    }

    private boolean samePair(AssetRelation relation, long source, long target, RelationType relationType) {
        if (relationType == RelationType.MATCHES) {
            return (relation.sourceAssetId() == source && relation.targetAssetId() == target)
                    || (relation.sourceAssetId() == target && relation.targetAssetId() == source);
        }
        return relation.sourceAssetId() == source && relation.targetAssetId() == target;
    }

    private void rejectContainsCycle(long sourceAssetId, long targetAssetId) {
        if (reaches(targetAssetId, sourceAssetId, containsGraph(Set.of()))) {
            throw new AssetRelationConflictException("包含关系不能形成循环");
        }
    }

    private void rejectContainsCycleExcluding(long sourceAssetId, long targetAssetId, long excludedId) {
        if (reaches(targetAssetId, sourceAssetId, containsGraph(Set.of(excludedId)))) {
            throw new AssetRelationConflictException("包含关系不能形成循环");
        }
    }

    private Map<Long, Set<Long>> containsGraph(Set<Long> excludedIds) {
        var graph = new HashMap<Long, Set<Long>>();
        for (var relation : assets.findAllRelations()) {
            if (relation.relationType() != RelationType.CONTAINS || excludedIds.contains(relation.id())) continue;
            graph.computeIfAbsent(relation.sourceAssetId(), key -> new HashSet<>()).add(relation.targetAssetId());
        }
        return graph;
    }

    private boolean reaches(long from, long goal, Map<Long, Set<Long>> graph) {
        var visited = new HashSet<Long>();
        return reaches(from, goal, graph, visited);
    }

    private boolean reaches(long from, long goal, Map<Long, Set<Long>> graph, Set<Long> visited) {
        if (from == goal) return true;
        if (!visited.add(from)) return false;
        for (var next : graph.getOrDefault(from, Set.of())) {
            if (reaches(next, goal, graph, visited)) return true;
        }
        return false;
    }

    private Asset requireAsset(long assetId) {
        return assets.findById(assetId)
                .orElseThrow(() -> new AssetNotFoundException(assetId));
    }

    private AssetRelation requireRelation(long relationId) {
        return assets.findRelationById(relationId)
                .orElseThrow(() -> new IllegalArgumentException("资产关系不存在：" + relationId));
    }

    private void audit(String action, long relationId, AssetRelation before, AssetRelation after,
            String operatorUserId, Instant now) {
        try {
            var payload = new HashMap<String, Object>();
            payload.put("before", before == null ? null : snapshot(before));
            payload.put("after", after == null ? null : snapshot(after));
            operationLogs.append(new OperationLog(0, operatorUserId, action, "ASSET_RELATION", relationId,
                    objectMapper.writeValueAsString(payload), now));
        } catch (Exception exception) {
            throw new IllegalStateException("关系变更审计写入失败", exception);
        }
    }

    private Map<String, Object> snapshot(AssetRelation relation) {
        return Map.of(
                "sourceAssetId", relation.sourceAssetId(),
                "targetAssetId", relation.targetAssetId(),
                "relationType", relation.relationType().name(),
                "description", relation.description() == null ? "" : relation.description());
    }

    private AssetRelation withDirectionLabel(AssetRelation relation, String label) {
        return new AssetRelation(relation.id(), relation.sourceAssetId(), relation.targetAssetId(),
                relation.targetAssetNumber(), relation.targetAssetName(), relation.targetAssetType(),
                relation.targetAssetStatus(), relation.relationType(), label, relation.primaryScope(),
                relation.description(), relation.createdBy(), relation.createdAt(),
                relation.updatedBy(), relation.updatedAt(), relation.version());
    }

    private String reverseLabel(RelationType relationType) {
        return labels(relationType)[1];
    }

    private String[] labels(RelationType relationType) {
        var labels = DIRECTION_LABELS.get(relationType);
        return labels == null ? new String[] { relationType.name(), relationType.name() } : labels;
    }

    private String primaryScope(Asset asset) {
        return asset.scopes().stream().findFirst()
                .map(this::scopeLabel)
                .orElse("");
    }

    private String scopeLabel(AssetScope scope) {
        return String.join(" / ", java.util.stream.Stream.of(
                        scope.platformFamily().isBlank() ? scope.platform() : scope.platformFamily(),
                        scope.base(), scope.productionLine(), scope.processSection())
                .filter(value -> value != null && !value.isBlank())
                .toList());
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
