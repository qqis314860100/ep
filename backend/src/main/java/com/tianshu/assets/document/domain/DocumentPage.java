package com.tianshu.assets.document.domain;

import java.util.List;

public record DocumentPage(List<KnowledgeDocument> items, long total, int page, int perPage) {

    public DocumentPage {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
