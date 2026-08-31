# 事项跟踪：GitHub

本仓库的需求事项与规格存放在 `xiaoBaiJLong/ai-mcp-gateway` 的 GitHub Issues 中。在本地检出目录配置 Git 远程仓库前，使用 `gh` CLI 时必须携带 `--repo xiaoBaiJLong/ai-mcp-gateway`。

## 约定

- 通过 `gh issue create --repo xiaoBaiJLong/ai-mcp-gateway` 创建 Issue。
- 读取、列出、评论、编辑标签和关闭 Issue 时，使用对应的 `gh issue` 命令并限定该仓库。
- Pull Request 不作为分诊请求入口。

## 技能约定

当技能要求“发布到 Issue Tracker”时，创建 GitHub Issue。

当技能要求“获取相关任务”时，执行 `gh issue view <number> --comments --repo xiaoBaiJLong/ai-mcp-gateway`。
