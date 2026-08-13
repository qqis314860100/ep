package com.tianshu.assets.common.preview;

import java.util.Optional;

/**
 * 文档在线预览转换端口。将暂不支持浏览器原生渲染的办公文档格式
 * 转换为 PDF 后以内联方式提供预览。
 */
public interface DocumentPreviewConverter {

    boolean supports(String format);

    Optional<byte[]> toPdf(String format, byte[] source);
}
