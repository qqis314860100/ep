# RAG 检索层升级计划（embedding 选型 + 混合检索 + 向量库替换 + 评测门禁）

> 日期：2026-09-10　性质：research / 跨仓执行前设计（ep 侧勘察结论 + 目标契约与执行顺序；本文件不提交 ai-rag 代码）
> 关联：`docs/research/2026-09-09-ai-rag-generalization-plan.md`（契约泛化，ai-rag 侧已落地 namespace/scopes/X-Service-Key//extract/fileContentBase64）；`docs/plans/2026-09-09-ai-agent-platform-architecture.md`（L3 能力服务定位）
> 共识前提（2026-09-10 会话确认）：RAG 能力服务**保持 Python**（不迁 TS）；embedding 走平台托管小模型（形态 B，本地不内嵌权重）；「TS 主流」作用于应用层，不作用于 L3 能力服务。

## 1. 现状基线（勘察 2026-09-10）

| 项 | 现状（ai-rag 仓事实） |
| --- | --- |
| 栈 | Python FastAPI + ChromaDB + sentence-transformers `bge-small-zh-v1.5`（本地内嵌推理）+ DeepSeek/OpenAI 兼容 + pypdf/python-docx/markdown-it；约 1.4 万行含测试/评测 |
| 管道 | rewrite → 检索（top_k、keyword/LLM rerank 可切换）→ generate → refusal / evidence verification / confidence → usage_guard；`/rag/chat/stream` SSE |
| 泛化状态 | namespace / scopes / X-Service-Key / `/extract` / fileContentBase64 已按 ep 契约落地 → **升级不得回退契约面** |
| 语料 | `knowledge/` 14 篇（电池）；ep-docs namespace 尚未灌语料 |
| 评测 | `rag/evals` 薄（flowchart/knowledge_gap 类样本），**无检索 golden、无回归门禁** |
| 短板 | ① embedding 为 2023 级小模型；② ChromaDB 无原生稀疏/BM25 → 无混合检索；③ 换 embedding/向量库/检索均无评测可证 |

## 2. 升级目标

- **评测先行**：建三层评测（检索 golden / 端到端 / 拒答负例），一切更换先跑 diff，把「现状」先变成可复现数字。
- **Embedding**：`bge-small-zh-v1.5` → **Qwen3-Embedding（0.6B 起步，A/B 决定是否试 4B/8B）**；形态 B = 平台托管推理服务暴露 OpenAI 兼容 embedding 端点（平台无现成端点则自起 vLLM 托管服务）。
- **混合检索**：稠密 + 稀疏（BM25 / 模型 sparse）+ RRF 融合 + reranker 消融（候选 `bge-reranker` cross-encoder）。
- **向量库**：ChromaDB → **Qdrant**（首选：原生 dense+sparse、Python 客户端成熟）或 **LanceDB**（备选：嵌入式、零运维）；评测全绿后迁移，保留 namespace/scopes 语义。
- **契约铁律**：`/rag/*` 端点、SSE 事件序、`X-Service-Key`、namespace/scopes、ingest/extract schema **全部不变**；升级只发生在内部实现。

## 3. 评测方案（P0 交付物，先行落地）

**golden 集结构**（JSONL，置于 `rag/evals/`）：每条 `{ id, query, namespace?, scope?, expected: [{docId/assetId, anchor 片段或段号}], negative?: bool }`；起步 50–100 条，覆盖难样本：术语别名、跨文档、否定式提问、越权试探。

- 检索层指标：`Recall@k (k=5,10)`、`MRR`、`nDCG@k`；消融：仅向量 vs 混合(RRF) vs +reranker。
- 端到端：faithfulness / answer relevance（LLM judge **本地化**，走内网模型）；「引用必须命中检索结果」用**规则判定**，不依赖 judge。
- 拒答负例：范围外 / 无证据问题必须拒答或低置信降级（守 AssetScope 边界，比召回率更优先）。
- 语料：P0 用现有 14 篇电池语料建基线（评测逻辑与 namespace 无关）；ep-docs namespace 就绪后另补一套 golden。
- 运行形态：Python harness、命令行可跑、输出 JSON diff 报告；**评测与实现解耦**——将来即使实现换语言，评测可黑盒复用（HTTP 打服务）。

## 4. 执行顺序（Phase，按顺序在 ai-rag 仓执行）

| Phase | 内容 | 出口标准 |
| --- | --- | --- |
| **P0 基线评测** | 建 golden + harness，用当前 bge-small + Chroma 跑出 baseline | golden diff 报告可用；rag/tests + 契约自测全绿 |
| **P1 Embedding A/B** | Qwen3-0.6B（托管端点或临时本地）vs baseline，同 golden | diff 量化提升 → 决定切换与档位（是否试 4B/8B） |
| **P2 混合检索** | 在胜出 embedding 上加稀疏路 + RRF + reranker 消融（可先 Chroma+外接 FTS 验证增益） | 混合 vs 单路 diff；reranker 取舍有据 |
| **P3 向量库迁移** | Chroma → Qdrant（或 LanceDB），namespace→collection 映射保留，重灌 | P0 golden diff + 契约测试全绿，电池默认行为不变 |
| **P4 门禁化（可选）** | rag/evals 接入 CI/回归脚本；prompt/检索/embedding/向量库改动必跑 | 门禁脚本入库 |

## 5. 风险与开放项

- **平台侧确认（阻塞 P1，不阻塞 P0）**：embedding 托管形态（A=现成 API / B=需自起托管）、确切模型名与版本固定（Qwen3-Embedding-0.6B/4B/8B，维度与向量分布不得漂移）、配额与内网可达。
- 切分策略随 embedding 换代可能需重调（P1 一并验证 chunk 长度与段落锚点）。
- Qwen3-Embedding 原生支持 dense+sparse：若托管端点只给 dense，稀疏路用独立 BM25；若给 sparse，Qdrant sparse 直用。
- 电池 UI 与默认 namespace 行为回归（沿用泛化回归纪律）。
- P3 迁移窗口策略（双写/切换）在迁移前单独定。

## 6. 执行前提与收尾

- 跨仓写入需授权（本会话工作区仅 ep）；改动在 ai-rag 仓按其 EXECUTION_RULES/提交规范独立提交。
- 本文件不替代 ai-rag 仓的 PRD/设计；作为两仓检索升级的**契约与顺序基准**。
- ep 侧不受影响：本升级不改变 ep `HttpAiCapabilityClient` 契约；ai-rag 各 Phase 完成后 ep 仅需重灌/冒烟即可联调。
