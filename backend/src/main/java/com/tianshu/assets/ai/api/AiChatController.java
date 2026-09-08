package com.tianshu.assets.ai.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tianshu.assets.ai.application.AiChatService;
import com.tianshu.assets.ai.application.AiChatService.MessageView;
import com.tianshu.assets.ai.application.AiChatService.SessionView;
import com.tianshu.assets.ai.application.AiCapabilityClient.ChatCitations;
import com.tianshu.assets.ai.application.AiCapabilityClient.ChatDelta;
import com.tianshu.assets.ai.application.AiCapabilityClient.ChatDone;
import com.tianshu.assets.ai.application.AiCapabilityClient.ChatError;
import com.tianshu.assets.ai.application.AiCapabilityClient.ChatEvent;
import com.tianshu.assets.ai.application.AiCapabilityClient.ChatMeta;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * AI 问答会话 API（AI 一期 T3）：/chat 走 SSE（meta → delta* → citations → done/error），
 * 会话/历史走普通 JSON。前端只调用本控制器，永不直连能力服务。
 */
@RestController
@RequestMapping("/api/v1/ai")
public class AiChatController {

    private final AiChatService aiChatService;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AiChatController(AiChatService aiChatService) {
        this.aiChatService = aiChatService;
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(
            @RequestHeader(name = "X-User-Id", defaultValue = "demo-user") String userId,
            @RequestBody(required = false) ChatStartRequest request) {
        var emitter = new SseEmitter(120_000L);
        executor.execute(() -> {
            try {
                aiChatService.chat(
                        request == null ? Optional.empty() : Optional.ofNullable(request.sessionId()),
                        userId,
                        request == null ? "" : request.question(),
                        event -> writeEvent(emitter, event));
            } catch (Exception exception) {
                writeEvent(emitter, new ChatError("internal", "AI 问答处理失败，请稍后重试"));
            } finally {
                emitter.complete();
            }
        });
        return emitter;
    }

    @GetMapping("/sessions")
    public List<SessionView> sessions(
            @RequestHeader(name = "X-User-Id", defaultValue = "demo-user") String userId) {
        return aiChatService.listSessions(userId);
    }

    @PatchMapping("/sessions/{id}")
    public SessionView rename(
            @PathVariable long id,
            @RequestHeader(name = "X-User-Id", defaultValue = "demo-user") String userId,
            @RequestBody RenameRequest request) {
        return aiChatService.renameSession(id, userId, request.name());
    }

    @DeleteMapping("/sessions/{id}")
    public void delete(
            @PathVariable long id,
            @RequestHeader(name = "X-User-Id", defaultValue = "demo-user") String userId) {
        aiChatService.deleteSession(id, userId);
    }

    @GetMapping("/sessions/{id}/messages")
    public List<MessageView> messages(
            @PathVariable long id,
            @RequestHeader(name = "X-User-Id", defaultValue = "demo-user") String userId) {
        return aiChatService.messages(id, userId);
    }

    private void writeEvent(SseEmitter emitter, ChatEvent event) {
        try {
            switch (event) {
                case ChatMeta meta -> emitter.send(SseEmitter.event().name("meta")
                        .data(Map.of("sessionId", meta.sessionId(), "messageId", meta.messageId())));
                case ChatDelta delta -> emitter.send(SseEmitter.event().name("delta")
                        .data(Map.of("text", delta.text())));
                case ChatCitations citations -> emitter.send(SseEmitter.event().name("citations")
                        .data(Map.of("refs", citations.refs())));
                case ChatDone done -> emitter.send(SseEmitter.event().name("done")
                        .data(Map.of("usage", done.usage())));
                case ChatError error -> emitter.send(SseEmitter.event().name("error")
                        .data(Map.of("code", error.code(), "message", error.message())));
            }
        } catch (IOException exception) {
            throw new IllegalStateException("SSE 写入失败", exception);
        }
    }

    public record ChatStartRequest(Long sessionId, String question) {
    }

    public record RenameRequest(String name) {
    }
}
