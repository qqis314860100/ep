package com.tianshu.assets.asset.api;

import java.util.List;

public record PageResponse<T>(List<T> data, Meta meta) {

    public PageResponse {
        data = List.copyOf(data);
    }

    public record Meta(long total, int page, int perPage, int totalPages) {

        public static Meta of(long total, int page, int perPage) {
            var totalPages = perPage == 0 ? 0 : (int) Math.ceil((double) total / perPage);
            return new Meta(total, page, perPage, totalPages);
        }
    }
}
