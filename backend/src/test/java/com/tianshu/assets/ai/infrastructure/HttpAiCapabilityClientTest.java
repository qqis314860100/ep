package com.tianshu.assets.ai.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.tianshu.assets.ai.application.AiCapabilityClient.ChatCitations;
import com.tianshu.assets.ai.application.AiCapabilityClient.ChatDone;
import com.tianshu.assets.ai.application.AiCapabilityClient.ChatMessage;
import com.tianshu.assets.ai.application.AiCapabilityClient.ChatRequest;
import com.tianshu.assets.ai.application.AiCapabilityClient.DocumentRequest;
import com.tianshu.assets.ai.application.AiCapabilityException;
import com.tianshu.assets.ai.application.AiCapabilityException.AiCapabilityError;
import com.tianshu.assets.ai.domain.AiTargetScope;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HttpAiCapabilityClientTest {

    private HttpServer server;
    private int port;
    private final List<String> receivedHeaders = new CopyOnWriteArrayList<>();
    private final AtomicReference<String> receivedBody = new AtomicReference<>();
    private final AtomicReference<String> receivedPath = new AtomicReference<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        port = server.getAddress().getPort();
        server.createContext("/", this::handle);
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        var path = exchange.getRequestURI().getPath();
        receivedPath.set(path);
        receivedHeaders.clear();
        exchange.getRequestHeaders().forEach((key, values) ->
                values.forEach(value -> receivedHeaders.add(key + ": " + value)));
        receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));

        if (path.startsWith("/__unauthorized__")) {
            respond(exchange, 401, "{\"code\":\"unauthorized\"}");
            return;
        }
        if (path.startsWith("/__forbidden__")) {
            respond(exchange, 403, "{\"code\":\"forbidden\"}");
            return;
        }
        if (path.startsWith("/__server_error__")) {
            respond(exchange, 500, "internal error");
            return;
        }
        if (path.startsWith("/hang")) {
            try {
                Thread.sleep(1500);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            respond(exchange, 200, "{}");
            return;
        }
        if (path.startsWith("/__weird__")) {
            respond(exchange, 200, "event: nonsense\ndata: {}\n\n");
            return;
        }
        if (path.startsWith("/__notrailing__")) {
            respond(exchange, 200, "event: delta\ndata: {\"text\":\"无结尾空行的片段\"}");
            return;
        }
        respond(exchange, 200, route(path));
    }

    private void respond(HttpExchange exchange, int code, String body) throws IOException {
        var bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(code, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private String route(String path) {
        return switch (path) {
            case "/rag/chat/stream" -> """
                    event: meta
                    data: {"sessionId":"s1","messageId":"m1"}

                    event: delta
                    data: {"text":"流式第一段"}

                    event: delta
                    data: {"text":"流式第二段"}

                    event: citations
                    data: {"refs":[{"docId":"doc-1","location":"第3页","excerpt":"摘录内容","inScope":true}]}

                    event: done
                    data: {"usage":"{}"}

                    """;
            case "/rag/documents/ingest" -> "{\"ok\":true,\"message\":\"ingested\"}";
            default -> """
                    {"name":"抽取的命名建议","description":"","assetTypeCode":"THREE_DIMENSIONAL_MODEL",
                     "tags":["标签1"],"summary":"","categoryCode":"","scopeHints":[],
                     "evidence":["片段甲"],"confidence":0.88}
                    """;
        };
    }

    private HttpAiCapabilityClient client() {
        var properties = new AiCapabilityProperties();
        properties.setBaseUrl("http://localhost:" + port);
        properties.setApiKey("test-service-key");
        properties.setNamespace("ep-docs");
        return new HttpAiCapabilityClient(properties);
    }

    private HttpAiCapabilityClient clientTo(String suffix) {
        var properties = new AiCapabilityProperties();
        properties.setBaseUrl("http://localhost:" + port + suffix);
        properties.setApiKey("test-service-key");
        return new HttpAiCapabilityClient(properties);
    }

    private static ChatRequest chatRequest() {
        return new ChatRequest("ep-docs", new AiTargetScope("", "", "H03", "宁德基地", "", ""),
                List.of(new ChatMessage("user", "上轮问题")), "这是什么资产？");
    }

    @Test
    void streamsChatEventsInOrderAndSendsScopeButNoUserIdentity() {
        var events = new ArrayList<com.tianshu.assets.ai.application.AiCapabilityClient.ChatEvent>();
        client().streamChat(chatRequest(), events::add);

        assertThat(events).extracting(event -> event.getClass().getSimpleName())
                .containsExactly("ChatMeta", "ChatDelta", "ChatDelta", "ChatCitations", "ChatDone");
        assertThat(events).filteredOn(ChatCitations.class::isInstance)
                .flatExtracting(event -> ((ChatCitations) event).refs())
                .allSatisfy(citation -> {
                    assertThat(citation.docId()).isEqualTo("doc-1");
                    assertThat(citation.inScope()).isTrue();
                });
        assertThat(receivedPath.get()).isEqualTo("/rag/chat/stream");
        assertThat(receivedHeaders).anyMatch(header -> header.toLowerCase().contains("x-service-key: test-service-key"));
        var body = receivedBody.get();
        assertThat(body).contains("\"namespace\":\"ep-docs\"");
        assertThat(body).contains("\"base\":\"宁德基地\"");
        assertThat(body).contains("\"question\":\"这是什么资产？\"");
        assertThat(body).doesNotContain("userId");
    }

    @Test
    void flushesTrailingSseEventWithoutTerminatingBlankLine() {
        var events = new ArrayList<com.tianshu.assets.ai.application.AiCapabilityClient.ChatEvent>();
        clientTo("/__notrailing__")
                .streamChat(new ChatRequest("ep-docs", new AiTargetScope("", "", "", "", "", ""),
                        List.of(), "问题"), events::add);
        assertThat(events).singleElement()
                .isInstanceOf(com.tianshu.assets.ai.application.AiCapabilityClient.ChatDelta.class);
    }

    @Test
    void extractsMetadataFromEndpoint() {
        var result = client().extractMetadata(new DocumentRequest("ep-docs", "ASSET", 103, "输送模块", List.of()));

        assertThat(receivedPath.get()).isEqualTo("/rag/extract");
        assertThat(result.name()).isEqualTo("抽取的命名建议");
        assertThat(result.assetTypeCode()).isEqualTo("THREE_DIMENSIONAL_MODEL");
        assertThat(result.tags()).containsExactly("标签1");
        assertThat(result.evidence()).containsExactly("片段甲");
        assertThat(result.confidence()).isEqualTo(0.88);
    }

    @Test
    void ingestsDocument() {
        var result = client().ingestDocument(new DocumentRequest("ep-docs", "ASSET", 103, "输送模块布置数模", List.of()));

        assertThat(receivedPath.get()).isEqualTo("/rag/documents/ingest");
        assertThat(result.ok()).isTrue();
    }

    @Test
    void mapsUnauthorizedToAuthFailed() {
        assertThatThrownBy(() -> clientTo("/__unauthorized__").extractMetadata(
                new DocumentRequest("ep-docs", "ASSET", 1, "标题", List.of())))
                .isInstanceOf(AiCapabilityException.class)
                .extracting(exception -> ((AiCapabilityException) exception).error())
                .isEqualTo(AiCapabilityError.AUTH_FAILED);
    }

    @Test
    void mapsForbiddenToAuthFailed() {
        assertThatThrownBy(() -> clientTo("/__forbidden__").extractMetadata(
                new DocumentRequest("ep-docs", "ASSET", 1, "标题", List.of())))
                .isInstanceOf(AiCapabilityException.class)
                .extracting(exception -> ((AiCapabilityException) exception).error())
                .isEqualTo(AiCapabilityError.AUTH_FAILED);
    }

    @Test
    void mapsServerErrorToUnavailable() {
        assertThatThrownBy(() -> clientTo("/__server_error__").extractMetadata(
                new DocumentRequest("ep-docs", "ASSET", 1, "标题", List.of())))
                .isInstanceOf(AiCapabilityException.class)
                .extracting(exception -> ((AiCapabilityException) exception).error())
                .isEqualTo(AiCapabilityError.UNAVAILABLE);
    }

    @Test
    void mapsUnknownSseEventToProtocol() {
        assertThatThrownBy(() -> clientTo("/__weird__").streamChat(
                new ChatRequest("ep-docs", new AiTargetScope("", "", "", "", "", ""), List.of(), "问题"), ignored -> {
                }))
                .isInstanceOf(AiCapabilityException.class)
                .extracting(exception -> ((AiCapabilityException) exception).error())
                .isEqualTo(AiCapabilityError.PROTOCOL);
    }

    @Test
    void mapsSlowResponseToTimeout() {
        var properties = new AiCapabilityProperties();
        properties.setBaseUrl("http://localhost:" + port + "/hang");
        properties.setApiKey("test-service-key");
        properties.setRequestTimeoutMillis(200);
        assertThatThrownBy(() -> new HttpAiCapabilityClient(properties).extractMetadata(
                new DocumentRequest("ep-docs", "ASSET", 1, "标题", List.of())))
                .isInstanceOf(AiCapabilityException.class)
                .extracting(exception -> ((AiCapabilityException) exception).error())
                .isEqualTo(AiCapabilityError.TIMEOUT);
    }
}
