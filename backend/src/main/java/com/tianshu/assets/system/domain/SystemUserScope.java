package com.tianshu.assets.system.domain;

public record SystemUserScope(long id, String base, String productLine) {

    public SystemUserScope {
        base = base == null ? "" : base.trim();
        productLine = productLine == null ? "" : productLine.trim();
    }
}
