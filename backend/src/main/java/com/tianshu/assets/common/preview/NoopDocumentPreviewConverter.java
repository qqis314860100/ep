package com.tianshu.assets.common.preview;

import java.util.Optional;

/**
 * 无转换能力的默认实现：不声明支持任何格式，也不产生转换结果。
 * 用于测试便捷构造与未配置转换服务的降级路径。
 */
public final class NoopDocumentPreviewConverter implements DocumentPreviewConverter {

    @Override
    public boolean supports(String format) {
        return false;
    }

    @Override
    public Optional<byte[]> toPdf(String format, byte[] source) {
        return Optional.empty();
    }
}
