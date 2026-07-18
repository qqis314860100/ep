package com.tianshu.assets.asset.application;

public class AssetNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AssetNotFoundException(long id) {
        super("未找到数模资产：" + id);
    }
}
