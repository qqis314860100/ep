# ai-rag 能力服务泛化（跨仓执行前设计）

> 日期：2026-09-09　性质：research / 执行蓝图（ep 侧勘察结论 + 目标契约）
> 背景：ep AI 一期后端契约已实现（T1~T4），dev 用 Fake 全链路通过浏览器验收；
> 真实能力服务 = 泛化 `/Users/tomtong/Software/js/ai-rag`（独立仓、独立治理，本文件不提交其代码）。

## 已核实的契约漂移（ep HttpAiCapabilityClient ↔ ai-rag 现状）

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
