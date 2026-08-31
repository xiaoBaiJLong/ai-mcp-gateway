# 智能体工具快照与原子密钥替换

Tool Collection 只是可复用的配置模板。发布 Agent 配置时，系统原子替换其直接、去重的 Tool Assignment 快照，因此后续模板编辑不会改变运行时权限；重置 API Key 时原子启用一个新 Key 并禁用旧 Key，不存在双 Key 共存窗口。
