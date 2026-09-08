package com.tianshu.assets.document.application;

/** 文档发布成功后的钩子（由 AI 编目触发实现；无监听器时静默跳过）。 */
@FunctionalInterface
public interface DocumentPublishedListener {

    void onDocumentPublished(long documentId);
}
