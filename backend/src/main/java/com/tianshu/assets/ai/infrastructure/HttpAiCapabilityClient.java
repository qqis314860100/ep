package com.tianshu.assets.ai.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tianshu.assets.ai.application.AiCapabilityClient;
import com.tianshu.assets.ai.application.AiCapabilityClient.ChatCitations;
import com.tianshu.assets.ai.application.AiCapabilityClient.ChatDelta;
import com.tianshu.assets.ai.application.AiCapabilityClient.ChatDone;
import com.tianshu.assets.ai.application.AiCapabilityClient.ChatError;
import com.tianshu.assets.ai.application.AiCapabilityClient.ChatEvent;
import com.tianshu.assets.ai.application.AiCapabilityClient.ChatMeta;
import com.tianshu.assets.ai.application.AiCapabilityClient.ChatRequest;
import com.tianshu.assets.ai.application.AiCapabilityClient.Citation;
import com.tianshu.assets.ai.application.AiCapabilityClient.DocumentRequest;
import com.tianshu.assets.ai.application.AiCapabilityClient.ExtractionResult;
import com.tianshu.assets.ai.application.AiCapabilityClient.IngestResult;
import com.tianshu.assets.ai.application.AiCapabilityException;
import com.tianshu.assets.ai.application.AiCapabilityException.AiCapabilityError;
import com.tianshu.assets.ai.domain.AiTargetScope;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 泛化 AI 能力服务的 HTTP/SSE 实现（T2）：local/oceanbase 使用。
 * 契约端点：{base}/rag/chat/stream（SSE）、{base}/rag/documents/ingest、{base}/rag/extract；
 * 鉴权走 X-Service-Key；检索请求只携带 scope/namespace，不携带用户身份。
 * 失败映射：401/403→AUTH_FAILED、头部/响应超时（HttpTimeoutException）→TIMEOUT、
 * 其余 IOException/非 2xx→UNAVAILABLE、解析失败→PROTOCOL。
 * 注意：请求超时仅覆盖响应头到达；200 后 SSE 流中断的中途停顿由调用方（问答会话层）按整体超时兜底。
 */
@Component
@Profile({"local", "oceanbase"})
public class HttpAiCapabilityClient implements AiCapabilityClient {

    private final AiCapabilityProperties properties;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public HttpAiCapabilityClient(AiCapabilityProperties properties) {
        this(properties, defaultHttpClient(properties));
    }

    HttpAiCapabilityClient(AiCapabilityProperties properties, HttpClient httpClient) {
        this.properties = properties;
        this.httpClient = httpClient;
    }

    private static HttpClient defaultHttpClient(AiCapabilityProperties properties) {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMillis()))
                .build();
    }

    @Override
    public void streamChat(ChatRequest request, Consumer<ChatEvent> sink) {
        var uri = URI.create(endpoint("/rag/chat/stream"));
        var body = json(Map.of(
                "namespace", text(request.namespace(), properties.getNamespace()),
                "scope", scopePayload(request.scopeFilter()),
                "history", request.history(),
                "question", request.question()));
        try {
            var response = send(uri, body, "text/event-stream");
            if (response.statusCode() != 200) {
                throw statusException(response.statusCode(), readBody(response));
            }
            try (var reader = new BufferedReader(
                    new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                String pendingEvent = null;
                var data = new StringBuilder();
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) {
                        if (data.length() > 0) {
                            if (sink != null) {
                                sink.accept(parseEvent(pendingEvent, data.toString()));
                            }
                            data.setLength(0);
                            pendingEvent = null;
                        }
                        continue;
                    }
                    if (line.startsWith("event:")) {
                        pendingEvent = line.substring("event:".length()).trim();
                    } else if (line.startsWith("data:")) {
                        var payload = line.substring("data:".length());
                        if (payload.startsWith(" ")) {
                            payload = payload.substring(1);
                        }
                        if (data.length() > 0) {
                            data.append('\n');
                        }
                        data.append(payload);
                    }
                }
                if (data.length() > 0 && sink != null) {
                    sink.accept(parseEvent(pendingEvent, data.toString()));
                }
            }
        } catch (AiCapabilityException exception) {
            throw exception;
        } catch (java.net.http.HttpTimeoutException exception) {
            throw timeout(exception);
        } catch (IOException exception) {
            throw communicationFailure(exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw interrupted(exception);
        }
    }

    @Override
    public IngestResult ingestDocument(DocumentRequest request) {
        var uri = URI.create(endpoint("/rag/documents/ingest"));
        var response = sendExpecting(uri, json(documentPayload(request)));
        return parse(response, IngestResult.class);
    }

    @Override
    public ExtractionResult extractMetadata(DocumentRequest request) {
        var uri = URI.create(endpoint("/rag/extract"));
        var response = sendExpecting(uri, json(documentPayload(request)));
        return parse(response, ExtractionResult.class);
    }

    private HttpResponse<java.io.InputStream> sendExpecting(URI uri, String body) {
        try {
            var response = send(uri, body, "application/json");
            if (response.statusCode() != 200) {
                throw statusException(response.statusCode(), readBody(response));
            }
            return response;
        } catch (java.net.http.HttpTimeoutException exception) {
            throw timeout(exception);
        } catch (IOException exception) {
            throw communicationFailure(exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw interrupted(exception);
        }
    }

    private <T> T parse(HttpResponse<java.io.InputStream> response, Class<T> type) {
        try (var input = response.body()) {
            return objectMapper.readValue(input, type);
        } catch (IOException exception) {
            throw new AiCapabilityException(AiCapabilityError.PROTOCOL,
                    "AI 能力服务响应解析失败: " + exception.getMessage(), exception);
        }
    }

    private HttpResponse<java.io.InputStream> send(URI uri, String body, String accept)
            throws IOException, InterruptedException {
        var builder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMillis(properties.getRequestTimeoutMillis()))
                .header("Content-Type", "application/json")
                .header("Accept", accept)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        var apiKey = properties.getApiKey();
        if (apiKey != null && !apiKey.isBlank()) {
            builder.header("X-Service-Key", apiKey);
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
    }

    private ChatEvent parseEvent(String eventName, String dataJson) {
        try {
            var node = objectMapper.readTree(dataJson);
            if (eventName == null) {
                eventName = node.path("type").asText("").isBlank() ? "" : node.path("type").asText();
            }
            return switch (eventName) {
                case "meta" -> new ChatMeta(text(node, "sessionId"), text(node, "messageId"));
                case "delta" -> new ChatDelta(node.path("text").asText(""));
                case "citations" -> new ChatCitations(parseCitations(node));
                case "done" -> new ChatDone(node.path("usage").asText(""));
                case "error" -> new ChatError(text(node, "code"), node.path("message").asText(""));
                default -> throw new AiCapabilityException(AiCapabilityError.PROTOCOL,
                        "未知 SSE 事件: " + eventName);
            };
        } catch (AiCapabilityException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new AiCapabilityException(AiCapabilityError.PROTOCOL,
                    "SSE 事件解析失败", exception);
        }
    }

    private List<Citation> parseCitations(JsonNode node) {
        var refs = new ArrayList<Citation>();
        var array = node.path("refs");
        if (array.isArray()) {
            array.forEach(item -> refs.add(new Citation(
                    item.path("docId").asText(""),
                    item.path("location").asText(""),
                    item.path("excerpt").asText(""),
                    item.path("inScope").asBoolean(true))));
        }
        return refs;
    }

    private AiCapabilityException statusException(int status, String body) {
        if (status == 401 || status == 403) {
            return new AiCapabilityException(AiCapabilityError.AUTH_FAILED,
                    "AI 能力服务鉴权失败 (" + status + ")");
        }
        return new AiCapabilityException(AiCapabilityError.UNAVAILABLE,
                "AI 能力服务不可用 (" + status + "): " + body);
    }

    private String readBody(HttpResponse<java.io.InputStream> response) throws IOException {
        try (var input = response.body()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String endpoint(String path) {
        var base = properties.getBaseUrl();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + path;
    }

    private Map<String, Object> documentPayload(DocumentRequest request) {
        return Map.of(
                "namespace", text(request.namespace(), properties.getNamespace()),
                "targetType", request.targetType(),
                "targetId", request.targetId(),
                "title", request.title(),
                "scopes", request.scopes());
    }

    private static AiCapabilityException timeout(java.net.http.HttpTimeoutException cause) {
        return new AiCapabilityException(AiCapabilityError.TIMEOUT,
                "AI 能力服务响应超时: " + cause.getMessage(), cause);
    }

    private static AiCapabilityException communicationFailure(IOException cause) {
        return new AiCapabilityException(AiCapabilityError.UNAVAILABLE,
                "AI 能力服务通信失败: " + cause.getMessage(), cause);
    }

    private static AiCapabilityException interrupted(InterruptedException cause) {
        return new AiCapabilityException(AiCapabilityError.UNAVAILABLE,
                "AI 能力服务请求被中断", cause);
    }

    private static Map<String, Object> scopePayload(AiTargetScope scope) {
        var payload = new LinkedHashMap<String, Object>();
        if (!scope.platformFamily().isBlank()) {
            payload.put("platformFamily", scope.platformFamily());
        }
        if (!scope.platformVariant().isBlank()) {
            payload.put("platformVariant", scope.platformVariant());
        }
        if (!scope.productLine().isBlank()) {
            payload.put("productLine", scope.productLine());
        }
        if (!scope.base().isBlank()) {
            payload.put("base", scope.base());
        }
        if (!scope.productionLine().isBlank()) {
            payload.put("productionLine", scope.productionLine());
        }
        if (!scope.processSection().isBlank()) {
            payload.put("processSection", scope.processSection());
        }
        return payload;
    }

    private static String text(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String text(JsonNode node, String field) {
        return node.path(field).asText("");
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalArgumentException("请求序列化失败", exception);
        }
    }
}
