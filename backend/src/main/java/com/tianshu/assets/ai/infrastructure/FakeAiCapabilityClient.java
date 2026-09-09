package com.tianshu.assets.ai.infrastructure;

import com.tianshu.assets.ai.application.AiCapabilityClient;
import com.tianshu.assets.ai.application.AiCapabilityClient.ChatCitations;
import com.tianshu.assets.ai.application.AiCapabilityClient.ChatDone;
import com.tianshu.assets.ai.application.AiCapabilityClient.ChatEvent;
import com.tianshu.assets.ai.application.AiCapabilityClient.ChatMeta;
import com.tianshu.assets.ai.application.AiCapabilityClient.ChatRequest;
import com.tianshu.assets.ai.application.AiCapabilityClient.Citation;
import com.tianshu.assets.ai.application.AiCapabilityClient.DocumentRequest;
import com.tianshu.assets.ai.application.AiCapabilityClient.ExtractionResult;
import com.tianshu.assets.ai.application.AiCapabilityClient.IngestResult;
import com.tianshu.assets.ai.application.AiCapabilityException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * dev 环境的 AI 能力服务替身（T2）：canned 事件流与抽取载荷，
 * 记录最近一次请求参数以便测试/联调断言；可用 setter 注入自定义序列或强制失败。
 */
public class FakeAiCapabilityClient implements AiCapabilityClient {

    private volatile List<ChatEvent> chatEvents = defaultChatEvents();
    private volatile ExtractionResult extractionResult = defaultExtractionResult();
    private volatile IngestResult ingestResult = new IngestResult(true, "ok");
    private volatile AiCapabilityException failure;
    private final List<Object> lastRequests = new CopyOnWriteArrayList<>();

    public FakeAiCapabilityClient() {
    }

    private static List<ChatEvent> defaultChatEvents() {
        return List.of(
                new ChatMeta("fake-session", "fake-message"),
                new ChatDelta("来自 Fake 能力服务的回答。"),
                new ChatCitations(List.of(new Citation("doc-1", "第 3 页", "示例摘录"))),
                new ChatDone("{\"promptTokens\":1}"));
    }

    private static ExtractionResult defaultExtractionResult() {
        return new ExtractionResult("资产名称建议", "描述建议", "THREE_DIMENSIONAL_MODEL",
                List.of("标签A"), "摘要建议", "", List.of(), List.of("证据片段"), 0.9);
    }

    /** 覆盖后续问答事件序列；传 null 恢复默认。 */
    public void setChatEvents(List<ChatEvent> events) {
        this.chatEvents = events == null ? defaultChatEvents() : List.copyOf(events);
    }

    /** 覆盖抽取载荷；传 null 恢复默认。 */
    public void setExtractionResult(ExtractionResult result) {
        this.extractionResult = result == null ? defaultExtractionResult() : result;
    }

    /** 让后续调用按指定错误失败（sticky，直到再次调用并传 null 清除），供上层降级联调/测试。 */
    public void failWith(AiCapabilityException exception) {
        this.failure = exception;
    }

    public List<Object> lastRequests() {
        return List.copyOf(lastRequests);
    }

    private <T> void capture(T request) {
        lastRequests.add(request);
        if (failure != null) {
            throw failure;
        }
    }

    @Override
    public void streamChat(ChatRequest request, java.util.function.Consumer<ChatEvent> sink) {
        capture(request);
        if (sink != null) {
            chatEvents.forEach(sink);
        }
    }

    @Override
    public IngestResult ingestDocument(DocumentRequest request) {
        capture(request);
        return ingestResult;
    }

    @Override
    public ExtractionResult extractMetadata(DocumentRequest request) {
        capture(request);
        return extractionResult;
    }
}
