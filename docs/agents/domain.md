# 领域文档

## 开始探索前

开始某个领域的工作前，先读取根目录 `CONTEXT.md` 和 `docs/adr/` 中所有相关 ADR。若计划变更与 ADR 冲突，必须明确指出冲突，而不是悄然覆盖。

## 文件结构

本仓库采用单一上下文：

```
/
├── CONTEXT.md
├── docs/adr/
├── mcp-gateway-server/src/
├── mock-user-service/src/
└── web-admin/src/
```

根目录只维护一份 `CONTEXT.md`，各模块共同遵循其中的术语和领域边界。

## 术语

在 Issue、规格、测试和实现讨论中，使用 `CONTEXT.md` 定义的术语表词汇。不得引入其中明确避免的同义词；若必要概念尚未定义，记录它以便后续进行领域建模。
