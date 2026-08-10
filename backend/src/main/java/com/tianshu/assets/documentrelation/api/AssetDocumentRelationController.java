package com.tianshu.assets.documentrelation.api;

import com.tianshu.assets.asset.api.AssetResponse;
import com.tianshu.assets.document.api.DocumentResponse;
import com.tianshu.assets.documentrelation.application.AssetDocumentRelationService;
import com.tianshu.assets.documentrelation.domain.AssetDocumentRelation;
import com.tianshu.assets.documentrelation.domain.AssetDocumentRelationType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AssetDocumentRelationController {

    private final AssetDocumentRelationService service;

    public AssetDocumentRelationController(AssetDocumentRelationService service) {
        this.service = service;
    }

    @GetMapping("/documents/{id}/asset-relations")
    public List<AssetRelationResponse> documentRelations(@PathVariable long id) {
        return service.byDocument(id).stream().map(relation -> AssetRelationResponse.from(relation,
                service.asset(relation.assetId()))).toList();
    }

    @GetMapping("/assets/{id}/documents")
    public List<DocumentRelationResponse> assetDocuments(@PathVariable long id) {
        return service.byAsset(id).stream().map(relation -> DocumentRelationResponse.from(relation,
                service.document(relation.documentId()))).toList();
    }

    @PostMapping("/asset-document-relations")
    @ResponseStatus(HttpStatus.CREATED)
    public RelationResponse create(@Valid @RequestBody CreateRequest request,
            @RequestHeader(name = "X-User-Id", defaultValue = "demo-user") String userId) {
        return RelationResponse.from(service.create(request.assetId(), request.documentId(), request.relationType(), userId));
    }

    @PatchMapping("/asset-document-relations/{id}")
    public RelationResponse changeType(@PathVariable long id, @Valid @RequestBody ChangeTypeRequest request,
            @RequestHeader(name = "X-User-Id", defaultValue = "demo-user") String userId) {
        return RelationResponse.from(service.changeType(id, request.relationType(), userId, request.version()));
    }

    @DeleteMapping("/asset-document-relations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable long id, @Valid @RequestBody RemoveRequest request,
            @RequestHeader(name = "X-User-Id", defaultValue = "demo-user") String userId) {
        service.remove(id, userId, request.version());
    }

    public record CreateRequest(@Positive long assetId, @Positive long documentId,
            @NotNull AssetDocumentRelationType relationType) {}
    public record ChangeTypeRequest(@NotNull AssetDocumentRelationType relationType, long version) {}
    public record RemoveRequest(long version) {}

    public record RelationResponse(long id, long assetId, long documentId, AssetDocumentRelationType relationType,
            long version) {
        static RelationResponse from(AssetDocumentRelation relation) {
            return new RelationResponse(relation.id(), relation.assetId(), relation.documentId(), relation.relationType(),
                    relation.version());
        }
    }

    public record AssetRelationResponse(RelationResponse relation, AssetResponse asset) {
        static AssetRelationResponse from(AssetDocumentRelation relation, com.tianshu.assets.asset.domain.Asset asset) {
            return new AssetRelationResponse(RelationResponse.from(relation), AssetResponse.from(asset));
        }
    }

    public record DocumentRelationResponse(RelationResponse relation, DocumentResponse document) {
        static DocumentRelationResponse from(AssetDocumentRelation relation,
                com.tianshu.assets.document.domain.KnowledgeDocument document) {
            return new DocumentRelationResponse(RelationResponse.from(relation), DocumentResponse.from(document));
        }
    }
}
