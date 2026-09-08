package com.tianshu.assets.ai.infrastructure;

import com.tianshu.assets.ai.application.AiCurateService;
import com.tianshu.assets.asset.application.AssetSavedListener;
import com.tianshu.assets.document.application.DocumentPublishedListener;
import com.tianshu.assets.governance.infrastructure.SpringGovernanceJobDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * AI 编目触发桥（T4）：资产业务保存/文档发布成功后经现有治理 JobDispatcher（事务提交后异步执行）
 * 投递编目任务；失败与投递失败均记录日志，不拖垮主业务写入。
 */
@Component
public class AiCurateListener implements AssetSavedListener, DocumentPublishedListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(AiCurateListener.class);

    private final SpringGovernanceJobDispatcher dispatcher;
    private final AiCurateService curateService;

    public AiCurateListener(SpringGovernanceJobDispatcher dispatcher, @Lazy AiCurateService curateService) {
        this.dispatcher = dispatcher;
        this.curateService = curateService;
    }

    @Override
    public void onAssetSaved(long assetId) {
        submit(() -> curateService.curateAsset(assetId), "资产", assetId);
    }

    @Override
    public void onDocumentPublished(long documentId) {
        submit(() -> curateService.curateDocument(documentId), "文档", documentId);
    }

    private void submit(Runnable task, String targetKind, long targetId) {
        Runnable wrapped = () -> {
            try {
                task.run();
            } catch (Exception exception) {
                LOGGER.error("AI 编目任务失败 {} id={}", targetKind, targetId, exception);
            }
        };
        try {
            dispatcher.dispatchTask(wrapped);
        } catch (Exception exception) {
            LOGGER.error("AI 编目任务投递失败 {} id={}", targetKind, targetId, exception);
        }
    }
}
