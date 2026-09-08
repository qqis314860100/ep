# 02: AI 能力服务 Port 与 fake/HTTP 客户端

**What to build:** ep 后端对「外部 AI 能力服务」只有一条访问通道：`AiCapabilityClient` 端口接口（流式问答 / 文档入库 / 元数据抽取三个能力），配一个可配置的 Fake 实现（canned 流式回答、抽取载荷，供 dev/单测/联调使用）和一个 HTTP+SSE 真实实现（指向泛化后的能力服务契约）。连接配置（base URL / 服务 key / 语料 namespace）只存在于后端配置，不进入前端。此后 ep 内一切 AI 调用只走该 Port——T3、T4 以此为基础，真实能力服务未就绪时全量功能可先用 Fake 跑通。

**Blocked by:** None（可与 01 并行开工）

**Status:** ready-for-agent

- [ ] `AiCapabilityClient` 接口按 spec 契约建模三个能力：流式问答（SSE 事件序 meta/delta/citations/done/error）、文档入库、元数据抽取
- [ ] Fake 实现：可按配置返回 canned 事件流与抽取载荷，测试可注入断言（调用了谁、带什么参数）
- [ ] HTTP/SSE 真实实现：按契约调用能力服务端点；服务 key/base URL/namespace 只从后端配置读取
- [ ] 检索请求只携带 scope 过滤条件（不携带用户身份）；能力服务密钥不出后端
- [ ] 超时/不可用按契约表现为明确失败（可被上层转成 SSE error 事件），不抛散乱异常
- [ ] 测试：接口契约的 fake 使用先例 + 真实实现的超时/鉴权失败路径（不连真实服务）

对应 spec 用户故事：30（密钥只在后端）、9/31（降级不拖垮主链路）。
