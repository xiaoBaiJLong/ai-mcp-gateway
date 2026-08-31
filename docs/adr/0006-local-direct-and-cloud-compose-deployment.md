# 本机直启与云端 Compose 部署

Windows 本机直接启动后端、前端和模拟服务，不使用 Docker；云端以 Docker Compose 运行应用组，并由 Nginx 统一暴露前端、管理 API 与 MCP Endpoint。MySQL 与 Nacos 属于挂载持久卷的长期基础设施，网关更新不应重启或删除它们；本机与云端模拟服务不得同时注册到共享 Nacos。
