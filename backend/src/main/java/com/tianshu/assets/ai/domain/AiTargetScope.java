package com.tianshu.assets.ai.domain;

import com.tianshu.assets.asset.domain.AssetScope;

/**
 * 建议目标的范围摘要（记录创建时刻目标的范围快照，用于越权过滤）。
 * 字段与资产业务的范围维度一致：蓝本/基地(base)、产品线(productLine)、拉线(productionLine)等。
 * 当前仅由资产范围映射；文档目标随入库触发切片落地后补充文档范围映射。
 */
public record AiTargetScope(
        String platformFamily,
        String platformVariant,
        String productLine,
        String base,
        String productionLine,
        String processSection) {

    public AiTargetScope {
        platformFamily = text(platformFamily);
        platformVariant = text(platformVariant);
        productLine = text(productLine);
        base = text(base);
        productionLine = text(productionLine);
        processSection = text(processSection);
    }

    public static AiTargetScope fromAssetScope(AssetScope scope) {
        return new AiTargetScope(
                scope.platformFamily(),
                scope.platformVariant(),
                scope.productLine(),
                scope.base(),
                scope.productionLine(),
                scope.processSection());
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
