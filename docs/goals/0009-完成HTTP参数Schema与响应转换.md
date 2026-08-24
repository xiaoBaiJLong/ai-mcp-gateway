# Goal：Issue #9 完成 HTTP 参数、Schema 与响应转换

> 来源：[GitHub Issue #9](https://github.com/xiaoBaiJLong/ai-mcp-gateway/issues/9)
> 当前状态：阻塞，禁止执行
> Blocked by：#6、#8
> Goal 生成基线：`main@87257483071514516913c4b566eba5aad80cc9a6`

## Goal（目标）

用统一工具模型支持手工和 OpenAPI 来源的常见 HTTP 参数、严格 Schema、受保护请求构造及 JSON/JSONPath/受限模板响应转换。

## Current state（当前状态）

- 生成时无完整映射、严格 Schema 或响应转换实现，全部验收未完成。
- #6、#8 完成后才可激活；激活时刷新评论、HEAD、现有最小调用路径、OpenAPI 草稿模型和验证命令。

## Execution order（执行顺序）

1. 验证阻塞任务完成，记录固定点。
2. 先建立数据驱动测试矩阵，覆盖方法、参数位置、JSON/Form、嵌套、默认/固定/隐藏和未知字段。
3. 统一手工与 OpenAPI 草稿到同一运行时工具定义和严格 `inputSchema` 校验。
4. 实现映射后二次校验、受保护 Header、凭据白名单和上游地址边界。
5. 实现结构化 JSON、JSONPath 和不可执行任意代码的受限模板，并加入 `outputSchema` 与大小边界验证。

## Completion criteria（完成标准）

- [ ] `GET`、`POST`、`PUT`、`PATCH`、`DELETE` 可映射到已登记 HTTP 上游。
- [ ] path、query、header、cookie 和 body 参数位置按定义工作。
- [ ] 请求体支持 `application/json` 和 `application/x-www-form-urlencoded`。
- [ ] 必填、默认、固定、隐藏、重命名和嵌套 JSON 组装行为均符合工具定义。
- [ ] 未在 `inputSchema` 声明的字段、错误类型和超限数据在访问上游前被拒绝。
- [ ] 映射后的最终请求再次校验，固定值、隐藏值、用户凭据、受保护 Header 和上游地址不能被 Agent 覆盖。
- [ ] 手工配置与 OpenAPI 导入使用同一运行时映射模型和错误语义。
- [ ] 响应支持结构化 JSON、JSONPath 提取和受限模板，模板不能执行任意代码或访问未允许上下文。
- [ ] 配置 `outputSchema` 时不合规结果不会返回给 Agent；JSONPath/模板失败返回明确工具错误。
- [ ] 数据驱动测试覆盖全部参数位置、嵌套、默认/固定/隐藏、三种响应模式和输出校验。
- [ ] 安全测试覆盖未知字段、受保护 Header、凭据白名单、任意 URL 阻断和模板逃逸。
- [ ] 相关测试及仓库门禁全部通过。
- [ ] 已以激活时 HEAD 为固定点完成代码审查，无未解决高严重度问题。
- [ ] 全部标准通过后创建聚焦本 ticket 的本地提交，工作区干净。

## Constraints（限制）

- 不支持文件上传下载、`multipart/form-data`、任意脚本、WebSocket、TCP、gRPC 或非 HTTP 上游。
- 模板只访问明确允许的上游 JSON 和调用上下文，不实现通用脚本引擎。
- 继续只允许登记的静态/Nacos 上游和 `user-passthrough` 业务认证。
- 不提前实现重试、熔断和分布式限流。
- 默认不 push、不关闭 Issue、不修改 tracker。

## Context（上下文）

- 来源：GitHub #1、#9；#9 生成时评论为空。
- 相关 ADR：ADR-0011、ADR-0012、ADR-0013、ADR-0019。
- 测试接缝：公开配置 API → `/mcp` tools/call → 可控 HTTP 上游请求捕获与响应变换。

## Session recommendation（会话建议）

- 会话：阻塞解除后的新单 ticket 目标会话。
- 能力级别：Advanced。
- 推理强度：High。
- 理由：Schema、参数映射、凭据保护、SSRF 和模板边界相互作用，错误可能导致越权请求或数据泄漏。
