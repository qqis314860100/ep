# ai-rag 能力服务泛化（跨仓执行前设计）

> 日期：2026-09-09　性质：research / 执行蓝图（ep 侧勘察结论 + 目标契约）
> 背景：ep AI 一期后端契约已实现（T1~T4），dev 用 Fake 全链路通过浏览器验收；
> 真实能力服务 = 泛化 `/Users/tomtong/Software/js/ai-rag`（独立仓、独立治理，本文件不提交其代码）。

## 复核结果（2026-09-12，ep 侧只读核对 ai-rag 源码）

> **结论：下表 6 处漂移已全部在 ai-rag 侧解决，下方「泛化改动清单」的 6 项均已落地。**
> 本文件保留为历史蓝图，不再作为待办依据。核对方式为只读阅读 ai-rag 源码，未改动其任何文件。

| 项 | 2026-09-09 勘察 | ai-rag 现状（2026-09-12 复核） | 状态 |
| --- | --- | --- | --- |
| 鉴权头 | ep 发 `X-Service-Key`，ai-rag 验 `X-API-Key` | `rag/app/api/routes.py` verify_api_key 已双头兼容：`headers.get("X-Service-Key") or headers.get("X-API-Key")`，注释明确「X-Service-Key 是外部 AI 能力服务（ep）约定的鉴权头…迁移期两者兼容」 | ✅ 已解决 |
| 流式问答 | 无 namespace/scopes，SSE 事件名未对齐 | `routes.py` chat_stream 内 `ep_mode = bool(req.headers.get("X-Service-Key"))`，注释写明 ep 模式下事件序对齐 `meta → delta* → citations → done`；另有 `_ep_citation_refs()` 产出 docId/location/excerpt/inScope | ✅ 已解决 |
| 文档入库 | ep payload 无文件字节（T4 运输层缺口） | ep 侧已发 `fileContentBase64` + `fileName`（HttpAiCapabilityClient.documentPayload:248-251）；ai-rag `schemas/models.py` IngestRequest 用 `AliasChoices` 同时接受 snake_case 与 camelCase，注释写明「file_path 或 file_content_base64 + file_name 二选一」 | ✅ 两侧均已补齐 |
| 元数据抽取 | ai-rag **无此端点** | `@router.post("/extract", response_model=ExtractionResult)` 已存在，响应模型名与 ep 对齐 | ✅ 已解决 |
| 命名空间隔离 | ai-rag 单 collection（电池） | `core/config.py` 增 `rag_namespace_default`（默认 `battery`）；pipeline 全链路接 `namespace` 参数 | ✅ 已解决 |
| 范围过滤 | ai-rag `/search` 无 scopes 维度 | `/search` 已接受 `namespace=request.namespace` 与 `scopes=request.scopes` | ✅ 已解决 |

**仍未验证的一层**：以上均为**源码层核对**，未做运行时冒烟。两仓联调的实际连通性（尤其是 base64 落临时文件后的解析链路、ep_mode 下 SSE 事件序的实际输出）需起真实服务验证，见文末「联调闭环」。

## 历史记录：勘察到的契约漂移（2026-09-09）

| 项 | ep 侧（既有代码事实） | ai-rag 现状（勘察 2026-09-09） | 处理 |
| --- | --- | --- | --- |
| 鉴权头 | `X-Service-Key`（HttpAiCapabilityClient.send） | `X-API-Key`（rag/app/api/routes.py verify_api_key） | **必须统一**（建议 ai-rag 侧改为读取 `X-Service-Key` 或两者兼容） |
| 流式问答 | `POST /rag/chat/stream`（请求含 namespace/scopes 列表/history/question；SSE meta/delta/citations/done/error） | 已有 `/rag/chat/stream`（无 namespace/scopes 概念、语料固定电池 namespace） | 加 namespace（默认电池）/scopes 过滤参数；SSE 事件名对齐 |
| 文档入库 | `POST /rag/documents/ingest`（payload: namespace,targetType,targetId,title,scopes；**无文件字节——T4 披露的运输层缺口**） | 已有 ingest（其 API schema 为文档元数据+内容） | 对齐 schema；文件内容运输方案在联调期定（base64/直传/引用存储 key） |
| 元数据抽取 | `POST /rag/extract` → ExtractionResult{name,description,assetTypeCode,tags,summary,categoryCode,scopeHints,evidence,confidence} | **无此端点** | 新增；输出 schema 与 ep AiCapabilityClient.ExtractionResult 逐字段对齐 |
| 命名空间隔离 | namespace（ep-docs 默认；电池语料保留独立 namespace） | 单 collection（电池） | Chroma 侧按 namespace 选 collection/前缀，新增 ep-docs |
| 范围过滤 | 检索请求 scopes[]（base/productLine 等维度，空=不限） | /search 无该过滤维度 | /chat 与 /search 支持 scopes 过滤（metadata filter） |

## 泛化改动清单（ai-rag 仓，按顺序）

1. **配置**：`RAG_NAMESPACE_DEFAULT`（默认保留电池语料现 collection）；`rag/app/core/config.py` 增 namespace 读取。
2. **鉴权头兼容**：`verify_api_key` 同时接受 `X-Service-Key` 与 `X-API-Key`（迁移期兼容，改读统一键后下线其一）。
3. **检索隔离**：retrieval/vector_store 按 namespace 解析 collection/前缀；chat/search/ingest 请求可选 `namespace`。
4. **范围过滤**：scopes 参数（对象数组：platformFamily/platformVariant/productLine/base/productionLine/processSection）→ 检索 metadata 过滤；空数组=不过滤。
5. **/extract**：新端点复用解析→切分→抽取（LLM 元数据抽取提示词），输出与 ep ExtractionResult 对齐；接入现有 usage_guard/refusal 纪律。
6. **回归**：电池语料现有 UI/API 全绿（默认 namespace 行为不变）；新增 ep-docs namespace 冒烟 + scopes 过滤测试。

## 执行前提与收尾

- 跨仓写入需授权（本会话工作区仅 ep）；改动在 ai-rag 仓按其 EXECUTION_RULES/提交规范独立提交。
- 联调闭环：ai-rag 就绪后 ep 换 `AiCapabilityClient` 真实实现（改 `ai.capability.base-url/api-key` 指向），跑真实 ingest/extract/chat 冒烟，再补上传/发布→建议→确认端到端与前端浏览器验收。
- 本文件不替代 ai-rag 仓的 PRD/设计；作为两仓契约基准。
