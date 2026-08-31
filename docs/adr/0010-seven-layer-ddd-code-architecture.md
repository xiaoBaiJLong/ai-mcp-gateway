# 七层 DDD 代码架构

后端统一采用 `api`、`app`、`case`、`domain`、`infrastructure`、`trigger`、`types` 七层架构。每层内部可按 Tool、Agent、Tool Collection、服务发现、运行时调用和验证台等业务主题继续组织包结构。

已确定的边界如下：

- `api` 仅定义稳定接口契约，不承载任何 Controller、HTTP DTO、Mapper 或持久化 DTO。
- `app` 只承载应用启动与装配职责，例如 Spring Boot 启动入口和 Bean 装配；不得承载业务用例或领域规则。
- `trigger` 是入站适配层，承载管理端 REST Controller、MCP Streamable HTTP 处理器及未来其他入站触发方式。
- 复杂业务流程由 `case` 编排多个 `domain` 能力；简单业务功能允许 `trigger` 直接调用 `domain`，不强制经过 `case`。
- `infrastructure` 是技术实现层，承载 MyBatis Mapper、持久化 DTO、MySQL 仓储实现、Nacos 客户端、OpenAPI 获取与解析客户端及下游 HTTP 调用实现。
- `domain` 只承载领域模型和业务规则，不能依赖 Spring、MyBatis、Nacos 或 HTTP 客户端等技术实现。
- `types` 只放跨层且确实通用的基础类型；不得成为无归属代码的收纳目录。

所有跨层协作遵循依赖倒置：调用方依赖稳定接口契约，而不依赖其他层的具体实现；`app` 负责在启动时装配接口与实现。尤其是 `trigger` 不得直接依赖 MyBatis Mapper、Nacos Client、OpenAPI Client 或下游 HTTP Client 等 `infrastructure` 实现。

接口的具体归属与完整依赖方向将在后续架构讨论中明确；不得通过将 Controller 或 Mapper 放入 `api` 来绕开当前边界。
