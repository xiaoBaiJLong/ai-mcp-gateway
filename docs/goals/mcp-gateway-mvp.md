# 跨上下文 Goal：完成企业内部 MCP 网关 MVP

> 来源：GitHub 父规格 [#1](https://github.com/xiaoBaiJLong/ai-mcp-gateway/issues/1) 及子任务 #2–#17
> Goal 生成基线：`main@87257483071514516913c4b566eba5aad80cc9a6`
> 执行形态：持久目标循环，每个 ticket 使用新上下文
> 当前前沿：[#2](https://github.com/xiaoBaiJLong/ai-mcp-gateway/issues/2)

## Goal（目标）

按 GitHub 原生子任务和 `Blocked by` 关系，逐个完成 #2–#17，使父规格 #1 的企业内部 MCP 网关 MVP 可以通过公开管理 REST API、统一 `/mcp` 端点和可控上游完成全部验收。

## Current state（当前状态）

- 生成时分支为 `main`，HEAD 为 `87257483071514516913c4b566eba5aad80cc9a6`，与 `origin/main` 一致。
- 生成前工作区没有实质代码改动；仅存在 Git 的行尾转换提示。
- 仓库只有中文需求、领域词汇和 19 个 ADR，没有前后端源码、构建脚本、CI 或测试。
- GitHub #1–#17 均为 `OPEN`，评论均为空，子任务均带 `ready-for-agent`。
- #2 没有阻塞，是唯一当前前沿；#3–#17 均被显式依赖阻塞。
- 所有产品功能与运行验证均可证实尚未实现；需求、测试接缝和架构决策已经文档化。

## Execution order（执行顺序）

1. 每轮重新读取父规格、所有开放子任务、评论和原生阻塞关系。
2. 只选择一个阻塞已全部解除的 `ready-for-agent` ticket。
3. 加载该 ticket 对应的 `docs/goals/NNNN-*.md`，在新上下文中刷新分支、HEAD、脏文件、已完成行为、缺口和验证命令。
4. 严格按子目标实施；开发中执行最小验证，完成前执行风险相称的集成或完整验证。
5. 以本轮开始时 HEAD 为固定点执行代码审查；全部完成标准通过后创建一个聚焦提交，并确认工作区干净。
6. 不自行 push、关闭 Issue 或修改 tracker；如执行授权包含这些外部动作，完成后再执行。
7. 重新计算前沿并进入下一轮，直到 #2–#17 全部达到各自完成标准。

推荐的单线程拓扑顺序为：`#2 → #3 → #4 → #5 → #6 → #7 → #8 → #9 → #10 → #11 → #12 → #13 → #14 → #15 → #16 → #17`。这不是跳过实时阻塞检查的许可。

## Completion criteria（完成标准）

- [ ] #2–#17 的每一个子 Goal 都已逐条满足其独立完成标准，并保存可复验的命令与结果摘要。
- [ ] 父规格 #1 的 12 条第一阶段验收标准均由自动化测试或可重复性能测试覆盖。
- [ ] 最高层黑盒接缝成立：公开管理 REST API 配置与发布 → 公开 `/mcp` 发现和调用 → 可控 Nacos、HTTP/远程 MCP 上游及审计结果。
- [ ] `2026-07-28`、`2025-03-26`、`2025-06-18`、`2025-11-25` 四个 Streamable HTTP 版本通过契约测试，legacy HTTP+SSE 被明确拒绝。
- [ ] 权限、API Key、用户凭据、SSRF、固定参数、受保护 Header、外部固定凭据和审计脱敏边界均有失败路径测试。
- [ ] 工具版本、配置原子切换、5 秒补偿收敛、Nacos 路由、重试、熔断、限流和并发规则均有自动化证据。
- [ ] 本地端到端环境可以按中文文档从干净状态启动，完整功能测试和仓库门禁全部通过。
- [ ] 持续 100 QPS 性能基线完成，报告包含环境、吞吐、P95、错误率、资源使用、瓶颈及通往 500 QPS 的实测差距。
- [ ] 最终代码审查覆盖从每个 ticket 固定点产生的改动，没有未解决的规格偏差或高严重度问题。
- [ ] 每个 ticket 对应一个聚焦提交，最终工作区干净且未修改无关文件。

## Constraints（限制）

- 只实现父规格 #1 和当前 ticket 明确包含的第一阶段范围；不得提前实现多租户、MCP 扩展、legacy HTTP+SSE、文件/非 HTTP 转换、destructive 发布、正式登录、Kubernetes 或高可用。
- 遵循 `AGENTS.md`：中文文档、最简单可维护实现、清晰模块职责、准确命名，以及只在非直观边界解释“为什么”的中文注释。
- 遵循未被 superseded 的 ADR；ADR-0007、0009、0014 已被替代，不能作为当前实现依据。
- MySQL、Redis、Nacos 之外不新增中间件，除非出现新需求并先形成 ADR。
- 不访问真实业务服务或真实数据，测试使用可控 Nacos、HTTP 和远程 MCP 夹具。
- 不修改无关脏文件，不把下游需求吸入当前 ticket。
- 默认允许完成标准通过后创建本地提交；默认不允许 push、PR、merge、关闭 Issue 或修改 tracker 状态。

## Context（上下文）

- 父规格：[#1](https://github.com/xiaoBaiJLong/ai-mcp-gateway/issues/1)
- 系统需求：`docs/requirements/mcp-gateway-requirements.md`
- 领域词汇：`CONTEXT.md`
- 架构决策：`docs/adr/`
- 逐 ticket 目标索引：`docs/goals/README.md`
- 统一测试接缝：公开管理 REST API → 公开 `/mcp` → 可控 Nacos/HTTP/远程 MCP 上游与审计。
- 每轮首先检查：`AGENTS.md`、ticket 正文与评论、相关 ADR、`git status --short --branch`、`git rev-parse HEAD`、最近提交、当前 diff、构建脚本和已有测试。

## Session recommendation（会话建议）

- 会话：持久目标循环，必须支持 ticket 之间创建新上下文并保留进度。
- 能力级别：Advanced。
- 推理强度：High。
- 理由：这是跨 16 个 ticket 的多会话项目，包含协议兼容、授权、安全边界、数据迁移、路由、并发和性能验证，单个上下文无法可靠完成。
