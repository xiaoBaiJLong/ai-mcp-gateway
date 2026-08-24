# 以最新 Streamable HTTP 为核心并兼容旧版本

网关以 MCP `2026-07-28` 的无会话 Streamable HTTP 模型作为内部核心协议，不实现 legacy HTTP+SSE 传输，同时通过边缘兼容层接入旧版 Streamable HTTP 客户端。这样既利用最新协议的无会话扩展性和请求头路由能力，又保留现有客户端兼容性；旧版 `initialize`、`Mcp-Session-Id` 和长连接状态不得渗透为核心领域约束。
