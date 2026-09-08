# ep AI 智能体平台架构（共享理解终稿）

> 日期：2026-09-09　状态：已确认（对话逐项拍板，见决策记录）　性质：架构决策笔记，作为后续 to-spec / to-tickets 的输入
> 关联代码库：`ep`（本仓库，Java + React）、`ai-rag`（/Users/tomtong/Software/js/ai-rag，只读参照，不在本次改动范围）
> 核心命题：ep 计划的 AI 智能体能力与现成 ai-rag 是「融合在一起」还是「拆开单独服务」→ 结论：**拆开成独立能力服务，契约集成**；只在前端产品层融合。

## 1. 背景与现状（已核实，2026-09-09）

| 项 | 现状 |
| --- | --- |
| ep | 仿真数模资产管理系统：Java 21 + Spring Boot + Spring JDBC + OceanBase（本机 local=MySQL）；React 18 + AntD。资产生命周期 `草稿→待整理→已标准化→已停用`；范围权限（AssetScope）、文档安全、审计、上传/检索/治理/盘点已成型。**无任何 AI/RAG 代码**。 |
| ai-rag | 电池产线 RAG 知识库：三服务分层 `web(React19+Tailwind) → api(Node+Express+SQLite) → rag(Python FastAPI+ChromaDB+DeepSeek)`。rag 已契约化：`/rag/*` 全端点带 `RAG_API_KEY`（health/search/chat/chat:stream/documents:ingest|reindex/diagram/knowledge-graph/gaps/extract 类）。工程纪律齐全（CI、执行规则、贡献规范、备份脚本、`rag/evals`、ruff、性能 smoke、多轮安全加固）。**成熟度判定：功能/产品壳成熟，部署为内网单机小规模（SQLite+Chroma 本地）**；语料仅 14 篇，功能跑在薄内容上，抗压未知。 |
| LLM | 公司内部部署有大模型平台，走 API（可当 OpenAI 兼容对待）——ep 内容不出公网，合规约束解除。 |
| 待建 | ep 的 AI 智能体项目：资料/资产智能问答（RAG）、上传自动整理与编目（建议）、治理/盘点助手、通用多智能体编排平台。产品定位尚未定案 → 本方案按「可演进通用形态」设计。 |

## 2. 目标

1. 给出「融合 vs 拆开」的架构结论与取舍依据。
2. 一张覆盖四种能力的、面向长期（多产品共享 agent 平台）的可演进目标架构。
3. 细化到端到端交互协议层（前端 → 后端 → AI 能力服务的调用链、鉴权、SSE、存储归属），并定出分期落地轮廓。
4. 产出后续 to-spec / to-tickets 的稳定输入。

## 3. 非目标

- 不改 ai-rag 仓库（泛化其能力服务是后续独立工作流，另行排期与提交）。
- 不写业务代码、不改现有资产业务行为。
- 不把电池产线产品壳/语料并入 ep。
- 一期不引入 Redis、编排层、MQ、商业观测产品。

## 4. 决策记录（2026-09-09 用户逐项确认）

### 顶层决策

- **D1 自治边界 = 建议-确认分层**：AI 对编目/命名/标签等只产「建议」（进待确认区）；治理/盘点类动作只出「动作建议清单」；人确认后由 ep 业务命令生效。AI 永不直写业务库。与 ep「提交可用+后台治理」哲学及归档 ADR 0017/0014 一致；人审线即 AI 服务与 ep 业务域的信任边界。
- **D2 LLM 接入 = 公司内部大模型平台 API**（OpenAI 兼容）；LLM 客户端在能力服务内做成可替换端口（内网 API / 未来外部 / 本地模型），默认指向内部平台。
- **D3 产品形态 = 统一「AI 智能体工作台」+ 关键业务页内嵌触发点**（上传页「AI 编目建议」、治理页「生成动作建议」）；前端无论后端如何拆都是 ep 这个 AntD SPA 的一部分。
- **D4 与 ai-rag 的关系 = 泛化其能力服务、独立部署**：把 ai-rag 的 rag 服务泛化为公司级 AI 能力服务——电池语料降级为一个命名空间，新增 ep 语料 namespace（ep-docs）、编目/抽取端点、建议提案 API；ai-rag 仓库继续独立演进；ep 语料是新 namespace，不与电池语料混灌。
- **D5 分期**：一期 = 语料管道 + 问答 + 编目建议 + 建议-确认工作台（独立可验收）；二期 = 编排层（L2）独立成服务 + 治理/盘点助手（首批 agent）。
- **D6 会话归属 = 库为真源**：会话/消息存 ep 业务库（可审计、可随权限/离职回收）；AI 能力服务无状态，每轮收「裁剪后历史 + scope」。Redis 只做热层（见细节 d3），永不当真源。

### 细节决策（维度逐项）

- **d1 编排层技术栈 = Python 自研薄运行时**（二期）：状态机（`running→waiting_confirm→done/failed`，确认门=可持久化暂停态）+ 预算/超时/重试 + 事件总线；LLM 调用用官方 OpenAI 兼容 SDK；**不绑任何编排框架**（确认门/审计/工具权限矩阵是强定制，通用框架要绕开）。参照系：Claude Code / DSH / Pi 均 Node，但它们是「活在终端/npm 生态的工具型 agent」；ep 的 L2 是内网常驻服务、长期邻居是 Python 能力服务与 Python 评测链，选 Python 使 ep 全栈运行时保持 Java + Python 两个（Node 则变三个）。
- **d2 多智能体演进 = 自研、分级**：二期先只支持单智能体+工具循环（覆盖 90% 场景）；出现并行拆任务需求先实现 orchestrator-worker（照 Claude Code subagent 语义：子 agent 独立上下文、只回报告）；真到复杂图编排再评估 LangGraph（只当图执行器，确认门/审计仍自持）。
- **d3 存储队列 = 库为真源 + Redis/Valkey 二期热层**：Redis 职责=热会话上下文(TTL)/运行中 agent 状态机快照/分布式锁/pub-sub(推 SSE)/用量计数，易失可重建；可靠作业（存量回灌、agent run 启动）走 DB 任务表+状态机+worker 抢占；轻量事件用 Redis Stream；跨服务事件放大/需重放再评估 MQ（NATS/RabbitMQ）。**一期不引 Redis**，但代码把 `SessionStore/StateStore` 抽象成接口，二期换 Redis 热层零侵入。
- **d4 工具体系 = 读自动/写提案-确认 + MCP 兼容**：工具契约 `{name, description, inputSchema, 执行宿主(ep域|平台), 读写类别, 幂等性, 超时}`；权限矩阵三层叠加并随请求注入 scope——①用户 AssetScope 数据可见边界（读工具强制校验）②工具级 ACL ③确认门分级（读=自动；写=永不直接执行，产建议提案→人确认→ep 命令生效）；每次工具调用审计（traceId/调用者/scope/参数摘要/结果/token/耗时）。工具注册表二期建于 L2 并兼容 MCP 协议（外部工具生态白捡；ep 域工具封装成 MCP server）。**一期无 agent，但工具契约与确认门先在「AI 建议」流程落地雏形**。
- **d5 能力服务粒度 = 单进程模块化 + worker 跑离线**：泛化后保持单进程多模块多 namespace；离线批量（存量回灌、批量编目）以同一服务 worker 模式跑（配合 DB 任务表）；出现负载/发布/故障隔离真实信号再拆进程（从模块边界切出，零重构）。
- **d6 评测与可观测 = 分级落地、开源自托管、不引商业观测**：一期=ep 语料评测集（检索 golden、端到端问答 golden、拒答负例防越权/幻觉）+ 内部 LLM judge 离线判分（忠实度/有用性/引用准确）+ traceId 贯穿 + 审计/日志分离；改动 prompt/检索/工具契约必跑评测集看 diff。二期=OpenTelemetry + 自托管面板 + agent 任务评测（结果正确性+关键步骤断言）+ 回归门禁。商业观测（LangSmith/Langfuse Cloud 等）=数据出内网/订阅/供应商绑定，排除。

## 5. 目标架构（七层）

```
L0 触点层        各产品前端：ep 的 AntD SPA——统一「AI 智能体工作台」+ 上传/治理页内嵌触发点
                 │  HTTPS/SSE
L1 产品后端层    ep Java/Spring/OceanBase：身份 · AssetScope 权限 · 会话真源(库) ·
                 建议-确认域 · ep 域工具执行宿主 · 审计        （每个产品各有一个 L1）
                 │  service key
L2 Agent 编排层  二期独立共享服务（Python 薄运行时）：Agent Runtime / Orchestrator /
                 Context Manager(Redis 热层) / Tool Registry(MCP 兼容) / Event Bus
                 │
L3 能力服务层    泛化 ai-rag/rag：单进程多模块多 namespace(ep-docs…)，worker 跑离线批量；
                 对问答工作台直接暴露 RAG API，对 L2 降格为「技能/工具」
L4 模型层        内部 LLM 平台（OpenAI 兼容）· 未来模型路由/预算网关

横切：traceId 贯穿（前端→ep→L2→L3→LLM）· 审计事件流 · 工具级权限矩阵 · 确认门
```

**关键认知**：编排层（L2）是「大 agent 平台」的本体；能力服务（L3）长期会从主角降格为它的技能之一。拓扑为 L2 留位，但**一期不建 L2**（薄流程先跑通，二期再立）。

## 6. 端到端交互细化

### 6.0 通信平面

| | 浏览器 → ep 后端 | ep 后端 → AI 能力服务 | AI 服务 → 内部 LLM |
| --- | --- | --- | --- |
| 协议 | HTTPS/JSON；问答 SSE | HTTPS/JSON；问答 SSE | OpenAI 兼容（流式） |
| 鉴权 | 用户 JWT（现有登录态） | 服务间 API key（内网） | LLM 平台 key（仅存在于 AI 服务） |
| 数据 | 会话/消息/建议（ep 业务域） | 语料检索/抽取（向量库，只读） | 无业务数据留存 |

铁律：浏览器永不直连 AI 服务；链路 `前端 → ep 后端 → AI 能力服务 → LLM`。

### 6.1 主流程 A：自然语言问答（一期核心，流式）

```
浏览器 POST /api/ai/chat {sessionId?, question}
ep 后端：鉴权 JWT → 解析用户+AssetScope → 取/建会话、读历史（ep 库）
         → POST {ai}/rag/chat/stream（不传用户身份，只传授权后的检索边界）
           headers X-Service-Key
           body { namespace:"ep-docs", scope:{...}, history:[最近N轮], question }
AI 服务：verify key → 按 scope/安全级过滤 + namespace 检索 → 组装 prompt（内部 LLM 配置）
         → 流式调 LLM → 后处理（证据引用结构、拒答/低置信降级，复用 ai-rag refusal/verification）
         → SSE 回 ep → ep 透传（可节流），流结束后整段消息+引用落会话表 → 浏览器渲染+证据卡片
```

SSE 事件约定（三层同契约）：`meta{sessionId,messageId}` → `delta{text}`* → `citations{refs}` → `done{usage}` | `error{code,message}`。

### 6.2 主流程 B：自动整理/编目建议（上传触发，一期）

```
上传入库成功（现状不动）→ 事务后领域事件 → 异步 Job（可重试）
→ POST {ai}/documents/ingest {namespace:"ep-docs", doc:{id,scope,安全级,文件}}
→ POST {ai}/extract → 返回{建议标题/分类/标签[]/摘要/关联建议/置信度/依据片段}
→ ep 写入「AI 建议」表（status=待确认，来源=AI，附 docId+依据），关联资产记录
→ 前端待确认清单提示 → 人确认/驳回 → 确认后 ep 业务命令生效 → 全程操作审计
```

存量回灌同一管道：后台任务分批喂 `ingest→extract→建议表`（DB 任务表 + worker，d3/d5）。

### 6.3 主流程 C：治理/盘点助手（二期预览）

```
浏览器 → ep 后端（发起「生成治理动作建议」）→ L2 agent 端点（规划→拆任务）
→ 需业务数据：L2 经工具注册表调 ep 受控只读 API（X-Service-Key + 用户 scope 上下文；
  AI 侧绝不直连 OceanBase）→ 产出动作建议清单 → 建议 API 回 ep → 人确认 → ep 命令执行
```

注：一期只开 `ep 后端 → AI 服务` 单向；二期因 agent 需要，再开 `AI/L2 → ep 受控只读` 面（安全面扩展，单独评审）。

## 7. 分期落地轮廓

| 期 | 建设内容 | 服务/运行时 |
| --- | --- | --- |
| 一期 | 泛化能力服务（namespace 化 + 编目/抽取/建议端点 + 评测集）；ep 侧 AI 建议-确认域 + AI 能力客户端；前端 AI 工作台 + 内嵌触发点；会话存 ep 库 | ep 后端（Java）+ AI 能力服务（Python） |
| 二期 | L2 编排运行时（Python）+ Redis/Valkey 热层；治理/盘点助手（首批 agent）；OTel 观测；agent 评测与门禁 | + L2（Python）+ Redis |

演进路径：一期会话量小不引 Redis，但存储接口抽象先行；能力服务未来全面「技能化」；多产品接入 L0/L1 后 L2 平台化；模型层加路由/预算网关。

## 8. 保留开放项（不阻塞架构）

- 部署环境与运维归属、命名空间命名。
- ai-rag 泛化的具体改造清单与排期（独立工作流，另开计划文件）。
- 数模/图档类二进制：只做元数据编目，不做全文问答。
- 一期评测集规模与内容（随 to-spec 细化）。
- 治理/盘点助手的首批具体动作清单（二期排期时定）。

## 9. 后续步骤

1. （可选）对一期范围跑 to-spec / to-tickets：垂直切片 = 能力服务泛化（namespace+编目端点）→ ep 建议-确认域 → 问答链路（SSE）→ 编目触发闭环 → 前端工作台。
2. 跨仓库契约评审机制：OpenAPI 契约随 ai-rag 仓库演进，ep 生成客户端；契约变更向后兼容优先。
