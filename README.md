# AI MCP Gateway

一个企业内部使用的 MCP 网关系统。它将业务服务的 HTTP API 配置化转换为 MCP 工具，也可以代理已有的远程 MCP 服务，并统一提供工具发现、Agent 权限、调用校验、服务发现、请求路由和审计能力。

第一阶段采用 Vue 3 独立前端和单个 Spring Boot 后端，使用 MySQL、Redis 与 Nacos，优先保证本地端到端运行。Agent 只连接一个 MCP 地址，并且只能发现和调用角色允许的工具。

## 项目文档

- [系统需求](docs/requirements/mcp-gateway-requirements.md)
- [领域词汇](CONTEXT.md)
- [架构决策](docs/adr/)
- [目标模式执行文档](docs/goals/README.md)
- [Agent 协作规则](AGENTS.md)

项目当前处于需求已确认、待进入实现阶段的状态。实现应遵循最简单可维护原则，不对延期范围进行预先设计。
