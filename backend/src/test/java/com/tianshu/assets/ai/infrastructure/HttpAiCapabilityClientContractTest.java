package com.tianshu.assets.ai.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.tianshu.assets.ai.application.AiCapabilityClient.DocumentRequest;
import com.tianshu.assets.ai.application.AiCapabilityClient.ExtractionResult;
import com.tianshu.assets.ai.application.AiCapabilityClient.IngestResult;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * ep 侧的消费者契约测试：用本地 stub 承载契约响应，断言 ep 发出的请求与解析的响应都符合两仓契约。
 *
 * <p>为什么需要它：ep 的 Jackson 配了 {@code FAIL_ON_UNKNOWN_PROPERTIES=false} —— ai-rag 新增字段
 * 会被静默忽略（安全），但**改名或删除字段会让对应 record 字段静默变成 null**，只在运行时暴露。
 * ai-rag 侧的 {@code test_capability_contract.py} 守的是它自己的出口，守不到 ep 的入口与解析。
 *
 * <p>不依赖外部服务：stub 用 JDK 自带 {@link HttpServer}，故可随 {@code mvn test} 执行。
 *
 * <p>fixture 来源：ingest 的形状取自 2026-09-12 对本机 rag 服务的实测响应；extract 的九个字段
 * 取自 `/openapi.json` 的 {@code ExtractionResult} schema（该端点实测需要真实文件才会返回 200，
 * 故未录制实例，字段名与类型与 schema 一致）。
 */
class HttpAiCapabilityClientContractTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String EXTRACT_FIXTURE = """
            {"name":"宁德-H03-电池包装配作业指导书","description":"装配步骤与力矩要求",
             "assetTypeCode":"MIXED_ASSET","tags":["装配","电池包"],
             "summary":"装配作业指导","categoryCode":"WORK_INSTRUCTION",
             "scopeHints":["宁德基地"],"evidence":["第 3 页力矩 25N·m"],"confidence":0.6}
            """;

    private static final String INGEST_FIXTURE = """
            {"document_id":"ep-docs:ASSET:123456","chunk_count":3,"index_status":"ready",
             "ok":true,"message":""}
            """;

    private HttpServer server;
    private String baseUrl;
    private volatile String lastPath;
    private volatile String lastServiceKey;
    private volatile JsonNode lastBody;

    @BeforeEach
    void startStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/rag/extract", exchange -> respond(exchange, EXTRACT_FIXTURE));
        server.createContext("/rag/documents/ingest", exchange -> respond(exchange, INGEST_FIXTURE));
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopStub() {
        server.stop(0);
    }

    private void respond(HttpExchange exchange, String fixture) throws IOException {
        lastPath = exchange.getRequestURI().getPath();
        lastServiceKey = exchange.getRequestHeaders().getFirst("X-Service-Key");
        lastBody = JSON.readTree(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        var bytes = fixture.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private HttpAiCapabilityClient client() {
        var properties = new AiCapabilityProperties();
        properties.setBaseUrl(baseUrl);
        properties.setApiKey("contract-test-key");
        properties.setNamespace("ep-docs");
        return new HttpAiCapabilityClient(properties,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
    }

    private DocumentRequest request() {
        return new DocumentRequest("ep-docs", "ASSET", 123456L, "标题",
                List.of(), "5rGL6K+V5YaS5rKZ", "smoke.txt");
    }

    @Test
    void extractSendsEpContractShapeAndParsesEveryField() {
        ExtractionResult result = client().extractMetadata(request());

        // 出参：契约的九个字段必须被正确解析（改名/删除会在此暴露为 null 或空）
        assertThat(result.name()).isEqualTo("宁德-H03-电池包装配作业指导书");
        assertThat(result.description()).isEqualTo("装配步骤与力矩要求");
        assertThat(result.assetTypeCode()).isEqualTo("MIXED_ASSET");
        assertThat(result.tags()).containsExactly("装配", "电池包");
        assertThat(result.summary()).isEqualTo("装配作业指导");
        assertThat(result.categoryCode()).isEqualTo("WORK_INSTRUCTION");
        assertThat(result.scopeHints()).containsExactly("宁德基地");
        assertThat(result.evidence()).containsExactly("第 3 页力矩 25N·m");
        assertThat(result.confidence()).isEqualTo(0.6);

        // 入参：ep 必须把契约字段名（camelCase）发给 ai-rag，路径与鉴权头也要对
        assertThat(lastPath).isEqualTo("/rag/extract");
        assertThat(lastServiceKey).isEqualTo("contract-test-key");
        assertThat(lastBody.get("namespace").asText()).isEqualTo("ep-docs");
        assertThat(lastBody.get("targetType").asText()).isEqualTo("ASSET");
        assertThat(lastBody.get("targetId").asLong()).isEqualTo(123456L);
        assertThat(lastBody.get("title").asText()).isEqualTo("标题");
        assertThat(lastBody.get("fileContentBase64").asText()).isEqualTo("5rGL6K+V5YaS5rKZ");
        assertThat(lastBody.get("fileName").asText()).isEqualTo("smoke.txt");
        assertThat(lastBody.has("scopes")).isTrue();
    }

    @Test
    void ingestParsesOkAndMessageAndDoesNotLeakUnknownFields() {
        IngestResult result = client().ingestDocument(request());

        assertThat(result.ok()).isTrue();
        assertThat(result.message()).isEmpty();
        assertThat(lastPath).isEqualTo("/rag/documents/ingest");
        assertThat(lastServiceKey).isEqualTo("contract-test-key");
    }
}
