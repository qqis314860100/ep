package com.tianshu.assets.ai.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tianshu.assets.ai.application.AiCapabilityClient.ChatCitations;
import com.tianshu.assets.ai.application.AiCapabilityClient.ChatDone;
import com.tianshu.assets.ai.application.AiCapabilityClient.ChatMeta;
import com.tianshu.assets.ai.application.AiCapabilityClient.ChatRequest;
import com.tianshu.assets.ai.application.AiCapabilityClient.Citation;
import com.tianshu.assets.ai.application.AiCapabilityClient.DocumentRequest;
import com.tianshu.assets.ai.application.AiCapabilityClient.ExtractionResult;
import com.tianshu.assets.ai.application.AiCapabilityClient.IngestResult;
import com.tianshu.assets.ai.application.AiCapabilityException;
import com.tianshu.assets.ai.application.AiCapabilityException.AiCapabilityError;
import com.tianshu.assets.ai.domain.AiTargetScope;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class FakeAiCapabilityClientTest {

    @Test
    void streamChatEmitsDefaultCannedEventsAndCapturesRequest() {
        var fake = new FakeAiCapabilityClient();
        var events = new ArrayList<com.tianshu.assets.ai.application.AiCapabilityClient.ChatEvent>();
        fake.streamChat(new ChatRequest("ep-docs",
                        new AiTargetScope("", "", "H03", "宁德基地", "", ""),
                        List.of(new com.tianshu.assets.ai.application.AiCapabilityClient.ChatMessage("user", "上轮")),
                        "这是什么资产？"),
                events::add);

        assertThat(events).hasSize(4);
        assertThat(events.get(0)).isInstanceOf(ChatMeta.class);
        assertThat(events.get(1)).isInstanceOf(com.tianshu.assets.ai.application.AiCapabilityClient.ChatDelta.class);
        assertThat(events.get(2)).isInstanceOf(ChatCitations.class);
        assertThat(events.get(3)).isInstanceOf(ChatDone.class);

        var request = fake.lastRequests().stream()
                .filter(ChatRequest.class::isInstance)
                .map(ChatRequest.class::cast)
                .findFirst().orElseThrow();
        assertThat(request.question()).isEqualTo("这是什么资产？");
        assertThat(request.scopeFilter().base()).isEqualTo("宁德基地");
        assertThat(request.history()).hasSize(1);
    }

    @Test
    void extractionReturnsConfiguredResultAndCapturesRequest() {
        var fake = new FakeAiCapabilityClient();
        fake.setExtractionResult(new ExtractionResult("新名字", "新描述", "THREE_DIMENSIONAL_MODEL",
                List.of("标签"), "", "", List.of(), List.of("证据"), 0.85));

        var result = fake.extractMetadata(new DocumentRequest("ep-docs", "ASSET", 103, "输送模块", List.of()));

        assertThat(result.name()).isEqualTo("新名字");
        assertThat(result.confidence()).isEqualTo(0.85);
        var request = fake.lastRequests().stream()
                .filter(DocumentRequest.class::isInstance)
                .map(DocumentRequest.class::cast)
                .findFirst().orElseThrow();
        assertThat(request.targetId()).isEqualTo(103);
    }

    @Test
    void ingestCapturesRequest() {
        var fake = new FakeAiCapabilityClient();
        var result = fake.ingestDocument(
                new DocumentRequest("ep-docs", "ASSET", 103, "输送模块布置数模", List.of()));
        assertThat(result).isEqualTo(new IngestResult(true, "ok"));
        assertThat(fake.lastRequests()).hasSize(1);
    }

    @Test
    void failWithThrowsTypedException() {
        var fake = new FakeAiCapabilityClient();
        fake.failWith(new AiCapabilityException(AiCapabilityError.UNAVAILABLE, "服务不可用"));
        assertThatThrownBy(() -> fake.streamChat(new ChatRequest("ep-docs",
                        new AiTargetScope("", "", "", "", "", ""), List.of(), "问题"), ignored -> {
                }))
                .isInstanceOf(AiCapabilityException.class)
                .extracting(exception -> ((AiCapabilityException) exception).error())
                .isEqualTo(AiCapabilityError.UNAVAILABLE);
    }
}
