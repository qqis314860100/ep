package com.tianshu.assets.ai.application;

import com.tianshu.assets.ai.domain.AiTargetScope;
import java.util.List;

/**
 * AI 能力服务端口（T2）：ep 后端访问泛化 AI 能力服务的唯一通道。
 *
 * <p>能力契约（与泛化后的能力服务 HTTP 契约一致，详见架构笔记 §6）：
 * <ul>
 *   <li>流式问答：SSE 事件序 meta → delta* → citations → done（或中途 error）；</li>
 *   <li>文档入库与元数据抽取：同步 JSON。</li>
 * </ul>
 * 检索请求只携带 scope 过滤条件与语料 namespace，不携带任何用户身份。</p>
 *
 * <p>实现：运行时唯一实现为 {@code HttpAiCapabilityClient}（真实能力服务，rag/…/extract）；
 * 离线单测使用 test 源内的 Fake 替身。失败一律抛 {@link AiCapabilityException}。</p>
 */
public interface AiCapabilityClient {

    /** 流式问答：把能力服务的 SSE 事件逐一喂给 sink；传输失败抛 AiCapabilityException。 */
    void streamChat(ChatRequest request, java.util.function.Consumer<ChatEvent> sink);

    /** 文档入库（解析/切分/向量化）。 */
    IngestResult ingestDocument(DocumentRequest request);

    /** 元数据抽取（编目建议载荷）。 */
    ExtractionResult extractMetadata(DocumentRequest request);

    // ---- 问答 ----

    record ChatRequest(
            String namespace,
            List<AiTargetScope> scopes,
            List<ChatMessage> history,
            String question) {

        public ChatRequest {
            namespace = namespace == null ? "" : namespace.trim();
            scopes = scopes == null ? List.of() : List.copyOf(scopes);
            history = history == null ? List.of() : List.copyOf(history);
            question = question == null ? "" : question.trim();
        }
    }

    record ChatMessage(String role, String content) {

        public ChatMessage {
            role = role == null ? "" : role.trim();
            content = content == null ? "" : content;
        }
    }

    /** SSE 问答事件（与能力服务契约同构）。 */
    sealed interface ChatEvent permits ChatMeta, ChatDelta, ChatCitations, ChatDone, ChatError {
    }

    record ChatMeta(String sessionId, String messageId) implements ChatEvent {
    }

    record ChatDelta(String text) implements ChatEvent {
    }

    record ChatCitations(List<Citation> refs) implements ChatEvent {

        public ChatCitations {
            refs = refs == null ? List.of() : List.copyOf(refs);
        }
    }

    record Citation(String docId, String location, String excerpt, boolean inScope) {

        public Citation(String docId, String location, String excerpt) {
            this(docId, location, excerpt, true);
        }

        public Citation {
            docId = docId == null ? "" : docId;
            location = location == null ? "" : location;
            excerpt = excerpt == null ? "" : excerpt;
        }
    }

    record ChatDone(String usage) implements ChatEvent {

        public ChatDone {
            usage = usage == null ? "" : usage;
        }
    }

    record ChatError(String code, String message) implements ChatEvent {

        public ChatError {
            code = code == null ? "" : code;
            message = message == null ? "" : message;
        }
    }

    // ---- 文档入库与抽取 ----

    /**
     * 入库/抽取的目标文档请求（命名空间 + 目标标识 + 范围 + 可选文件字节）。
     * 文件内容/版本经 fileContentBase64 + fileName（需带扩展名，服务端据此选解析器）运输；
     * 二者为空时仅登记元数据（能力服务将返回缺文件错误）。返回的 docId 语义见能力服务契约。
     */
    record DocumentRequest(
            String namespace,
            String targetType,
            long targetId,
            String title,
            List<AiTargetScope> scopes,
            String fileContentBase64,
            String fileName) {

        public DocumentRequest {
            namespace = text(namespace);
            targetType = text(targetType);
            title = text(title);
            scopes = scopes == null ? List.of() : List.copyOf(scopes);
            fileContentBase64 = text(fileContentBase64);
            fileName = text(fileName);
        }

        public DocumentRequest(String namespace, String targetType, long targetId, String title,
                List<AiTargetScope> scopes) {
            this(namespace, targetType, targetId, title, scopes, "", "");
        }

        private static String text(String value) {
            return value == null ? "" : value.trim();
        }
    }

    record IngestResult(boolean ok, String message) {
    }

    /** 编目抽取载荷：字段按目标类型解释（资产用 name/description/assetTypeCode/tags，文档用 summary/categoryCode）。 */
    record ExtractionResult(
            String name,
            String description,
            String assetTypeCode,
            List<String> tags,
            String summary,
            String categoryCode,
            List<String> scopeHints,
            List<String> evidence,
            Double confidence) {

        public ExtractionResult {
            name = text(name);
            description = text(description);
            assetTypeCode = text(assetTypeCode);
            summary = text(summary);
            categoryCode = text(categoryCode);
            tags = tags == null ? List.of() : List.copyOf(tags);
            scopeHints = scopeHints == null ? List.of() : List.copyOf(scopeHints);
            evidence = evidence == null ? List.of() : List.copyOf(evidence);
        }

        private static String text(String value) {
            return value == null ? "" : value.trim();
        }
    }
}
