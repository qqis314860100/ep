package com.tianshu.assets.search.api;

import com.tianshu.assets.asset.api.AssetResponse;
import com.tianshu.assets.asset.domain.AssetPage;
import com.tianshu.assets.document.api.DocumentResponse;
import com.tianshu.assets.document.domain.DocumentPage;
import java.util.List;

public record UnifiedSearchResponse(Section<AssetResponse> assets, Section<DocumentResponse> documents) {

    public static UnifiedSearchResponse from(AssetPage assets, DocumentPage documents) {
        return new UnifiedSearchResponse(Section.success(assets.items().stream().map(AssetResponse::from).toList(),
                assets.total(), assets.page(), assets.perPage()), Section.success(documents.items().stream()
                .map(DocumentResponse::from).toList(), documents.total(), documents.page(), documents.perPage()));
    }

    public record Section<T>(List<T> data, Meta meta, String status, String errorCode) {
        public Section { data = List.copyOf(data); }
        static <T> Section<T> success(List<T> data, long total, int page, int perPage) {
            return new Section<>(data, Meta.of(total, page, perPage), "SUCCESS", "");
        }
    }

    public record Meta(long total, int page, int perPage, int totalPages) {
        static Meta of(long total, int page, int perPage) {
            return new Meta(total, page, perPage, (int) Math.ceil((double) total / perPage));
        }
    }
}
