package com.tianshu.assets.asset.api;

import com.tianshu.assets.asset.application.AssetRelationService;
import com.tianshu.assets.asset.domain.AssetRelation;
import com.tianshu.assets.asset.domain.RelationType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 资产关系维护（RELATION-03/04/05）：新增、修改（含方向/类型/说明）、解除与双向查询。
 * 关系规则（不自环、不重复、包含无循环、方向约束）在服务端领域层校验。
 */
@Validated
@RestController
@RequestMapping("/api/v1/assets")
public class AssetRelationController {

    private final AssetRelationService relations;

    @Autowired
    public AssetRelationController(AssetRelationService relations) {
        this.relations = relations;
    }

    @GetMapping("/{assetId}/relations")
    public List<AssetRelation> list(@PathVariable @Min(1) long assetId) {
        return relations.findRelations(assetId);
    }

    @PostMapping("/{assetId}/relations")
    @ResponseStatus(HttpStatus.CREATED)
    public AssetRelation create(
            @PathVariable @Min(1) long assetId,
            @RequestHeader(name = "X-User-Id", defaultValue = "demo-user") String userId,
            @RequestHeader(name = "X-User-Name", defaultValue = "当前用户") String userName,
            @Valid @RequestBody CreateRelationRequest request) {
        return relations.create(assetId, request.targetAssetId(), request.relationType(),
                request.description(), userId, userName);
    }

    @PatchMapping("/{assetId}/relations/{relationId}")
    public AssetRelation update(
            @PathVariable @Min(1) long assetId,
            @PathVariable @Min(1) long relationId,
            @RequestHeader(name = "X-User-Id", defaultValue = "demo-user") String userId,
            @RequestHeader(name = "X-User-Name", defaultValue = "当前用户") String userName,
            @Valid @RequestBody UpdateRelationRequest request) {
        return relations.update(relationId, request.sourceAssetId(), request.targetAssetId(),
                request.relationType(), request.description(), request.version(), userId, userName);    }

    @DeleteMapping("/{assetId}/relations/{relationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(
            @PathVariable @Min(1) long assetId,
            @PathVariable @Min(1) long relationId,
            @RequestHeader(name = "X-User-Id", defaultValue = "demo-user") String userId) {
        relations.remove(relationId, userId);
    }

    public record CreateRelationRequest(
            @Min(1) long targetAssetId,
            @NotNull(message = "关系类型不能为空") RelationType relationType,
            String description) {}

    public record UpdateRelationRequest(
            @Min(1) long sourceAssetId,
            @Min(1) long targetAssetId,
            @NotNull(message = "关系类型不能为空") RelationType relationType,
            String description,
            @Min(0) long version) {}
}
