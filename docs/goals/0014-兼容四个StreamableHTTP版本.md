# Goal：Issue #14 兼容四个 Streamable HTTP 版本

> 来源：[GitHub Issue #14](https://github.com/xiaoBaiJLong/ai-mcp-gateway/issues/14)
> 当前状态：阻塞，禁止执行
> Blocked by：#6、#13
> Goal 生成基线：`main@87257483071514516913c4b566eba5aad80cc9a6`

## Goal（目标）

以 `2026-07-28` 为无会话核心，通过协议边缘适配同时兼容三个 2025 Streamable HTTP 版本的下游客户端和远程上游，并明确拒绝 legacy HTTP+SSE。

## Current state（当前状态）

- 生成时无四版本兼容层、会话隔离或协议契约套件，全部验收未完成。
- #6、#13 完成后才可激活；激活时刷新评论、HEAD、当前上下游协议实现、SDK/协议资料和验证命令。

## Execution order（执行顺序）

1. 验证阻塞任务完成，记录固定点并确认四个目标版本的权威协议资料。
2. 为四个版本分别建立下游初始化、`tools/list`、`tools/call`、错误和会话契约测试。
3. 保持最新无会话核心不变，只在边缘适配旧版初始化、`Mcp-Session-Id`、请求方式和可选 SSE 响应。
4. 为远程 MCP 上游建立相同四版本的客户端适配与会话隔离测试。
5. 增加版本不匹配、会话缺失/失效和 legacy HTTP+SSE 明确拒绝测试。

## Completion criteria（完成标准）

- [ ] `2026-07-28` 下游客户端可完成初始化、`tools/list` 和 `tools/call`。
- [ ] `2025-03-26` 下游客户端可完成初始化、`tools/list` 和 `tools/call`。
- [ ] `2025-06-18` 下游客户端可完成初始化、`tools/list` 和 `tools/call`。
- [ ] `2025-11-25` 下游客户端可完成初始化、`tools/list` 和 `tools/call`。
- [ ] 远程 MCP 上游的四个目标版本分别通过工具发现和调用测试。
- [ ] 旧版初始化、会话 ID 和长连接状态只存在于协议边缘，不改变核心授权、工具、路由和错误领域模型。
- [ ] Streamable HTTP 在协议需要时可从统一端点返回 `text/event-stream`，但不建立 legacy 双端点传输。
- [ ] 会话缺失、失效和协议版本不匹配返回符合对应版本且不泄密的错误。
- [ ] legacy HTTP+SSE、WebSocket 和 stdio 连接得到明确不支持结果。
- [ ] 四版本契约套件覆盖发现、调用、错误、目录变化和上下游会话隔离。
- [ ] 最新版本回归测试证明旧版适配未改变其无会话核心行为。
- [ ] 相关测试及仓库门禁全部通过。
- [ ] 已以激活时 HEAD 为固定点完成代码审查，无未解决高严重度问题。
- [ ] 全部标准通过后创建聚焦本 ticket 的本地提交，工作区干净。

## Constraints（限制）

- 不实现 legacy HTTP+SSE 双端点、WebSocket 或 stdio。
- 不让旧版会话状态渗透到核心领域服务和持久授权模型。
- 协议事实必须依据权威 MCP 规范或官方 SDK，不凭记忆猜测版本差异。
- 只实现 `tools/list` 与 `tools/call`，不实现其他 MCP 能力。
- 默认不 push、不关闭 Issue、不修改 tracker。

## Context（上下文）

- 来源：GitHub #1、#14；#14 生成时评论为空。
- 相关 ADR：ADR-0002、ADR-0003、ADR-0008。
- 测试接缝：四版下游客户端 → 统一 `/mcp`；网关 → 四版可控远程 MCP。

## Session recommendation（会话建议）

- 会话：阻塞解除后的新单 ticket 目标会话。
- 能力级别：Advanced。
- 推理强度：High。
- 理由：四个协议版本的双向兼容与会话隔离需要权威资料、长上下文综合和严格契约验证。
