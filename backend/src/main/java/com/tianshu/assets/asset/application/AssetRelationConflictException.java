package com.tianshu.assets.asset.application;

/** 资产关系规则冲突（自环、重复、包含循环、并发版本等），映射为 409。 */
public class AssetRelationConflictException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AssetRelationConflictException(String message) {
        super(message);
    }
}
