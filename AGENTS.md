## Agent 技能配置

### Issue 跟踪

本项目使用 GitHub Issues 跟踪问题、需求和规格说明，仓库为 `xiaoBaiJLong/ai-mcp-gateway`。具体规则参见 `docs/agents/issue-tracker.md`。

### Triage 标签

使用五个默认分诊标签：`needs-triage`、`needs-info`、`ready-for-agent`、`ready-for-human` 和 `wontfix`。具体映射和含义参见 `docs/agents/triage-labels.md`。

### 领域文档

本仓库采用单上下文（single-context）领域文档结构。处理代码前，应阅读根目录的 `CONTEXT.md` 和 `docs/adr/` 中与任务相关的架构决策。具体规则参见 `docs/agents/domain.md`。

### 文档语言

本项目的文档统一使用中文编写，包括但不限于 README、领域文档、架构决策记录、设计文档和开发说明。

技术标识符、命令、文件名、协议名称、API 字段及业内通用英文术语可以保留英文；必要时应补充中文解释。

### 实现原则

所有实现以满足当前已确认需求的最简单方案为准，不为尚未出现的场景预先设计复杂抽象、扩展框架或额外中间件。

在保持实现简单的同时，必须保证代码可维护、可读且模块职责清晰。命名应准确，函数和类保持聚焦，避免过深继承、隐式行为和无必要的设计模式。

在业务约束、协议兼容、安全边界或非直观取舍处补充适量中文注释，重点解释“为什么这样做”，不要用注释重复代码已经表达的内容。
