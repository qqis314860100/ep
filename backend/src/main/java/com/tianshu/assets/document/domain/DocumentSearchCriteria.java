package com.tianshu.assets.document.domain;

public record DocumentSearchCriteria(String query, String categoryCode, int page, int perPage) {

    public DocumentSearchCriteria {
        query = query == null ? "" : query.trim();
        categoryCode = categoryCode == null ? "" : categoryCode.trim();
        page = Math.max(page, 1);
        perPage = Math.min(Math.max(perPage, 1), 100);
    }
}
