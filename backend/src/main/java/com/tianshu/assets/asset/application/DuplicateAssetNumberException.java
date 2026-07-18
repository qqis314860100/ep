package com.tianshu.assets.asset.application;

public class DuplicateAssetNumberException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DuplicateAssetNumberException(String assetNumber) {
        super("资料编号已存在：" + assetNumber);
    }
}
