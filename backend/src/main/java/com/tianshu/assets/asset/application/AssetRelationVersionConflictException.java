package com.tianshu.assets.asset.application;

/** 资产关系并发版本冲突，映射为 409。 */
public class AssetRelationVersionConflictException extends AssetRelationConflictException {

    private static final long serialVersionUID = 1L;

    public AssetRelationVersionConflictException(String message) {
        super(message);
    }
}
