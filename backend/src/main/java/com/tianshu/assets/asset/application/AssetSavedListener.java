package com.tianshu.assets.asset.application;

/** 资产业务侧成功保存后的钩子（由 AI 编目触发实现；无监听器时静默跳过）。 */
@FunctionalInterface
public interface AssetSavedListener {

    void onAssetSaved(long assetId);
}
