package com.tianshu.assets.ai.domain;

/** 回答引用（域内类型；能力服务侧引用在应用层映射为本类型落库）。 */
public record AiCitation(String docId, String location, String excerpt, boolean inScope) {

    public AiCitation(String docId, String location, String excerpt) {
        this(docId, location, excerpt, true);
    }

    public AiCitation {
        docId = docId == null ? "" : docId;
        location = location == null ? "" : location;
        excerpt = excerpt == null ? "" : excerpt;
    }
}
