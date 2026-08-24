# Issue 跟踪方式：GitHub

本仓库的问题、需求和规格说明统一记录在 GitHub Issues：

`xiaoBaiJLong/ai-mcp-gateway`

所有 Issue 操作优先使用 `gh` CLI。

## 常用操作

- 创建 Issue：`gh issue create --title "..." --body "..."`
- 查看 Issue：`gh issue view <编号> --comments`
- 列出 Issue：`gh issue list --state open`
- 添加评论：`gh issue comment <编号> --body "..."`
- 添加标签：`gh issue edit <编号> --add-label "..."`
- 移除标签：`gh issue edit <编号> --remove-label "..."`
- 关闭 Issue：`gh issue close <编号> --comment "..."`

在当前仓库目录中运行时，通过 `git remote -v` 确定目标仓库；`gh` 通常会自动完成识别。

## Pull Request 是否参与分诊

**不将 Pull Request 作为需求入口。**

Pull Request 默认不进入 Issue 分诊队列。如果项目以后决定通过外部 Pull Request 接收需求，可以修改此处规则。

## 技能操作约定

当技能要求“发布到 Issue 跟踪器”时，应创建一个 GitHub Issue。

当技能要求“获取相关工单”时，应运行：

`gh issue view <编号> --comments`

读取 Issue 时，应同时关注正文、评论和标签，避免仅根据标题判断需求。

修改或关闭 Issue 时，应说明原因。信息不足时使用 `needs-info`；已经明确可以实施时，根据任务性质使用 `ready-for-agent` 或 `ready-for-human`。
