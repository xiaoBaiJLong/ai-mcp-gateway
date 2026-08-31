# 一个 Tool 对应一个 HTTP 映射

每个 MCP Tool 只映射一个 OpenAPI operation，以来源服务、HTTP 方法和规范化路径识别。Tool 定义与 HTTP Mapping 分别持久化，确保来源接口更新不覆盖管理员维护的 Tool 文案，也为未来非 HTTP 来源留出空间；网关层多接口编排被明确排除在首版之外。
