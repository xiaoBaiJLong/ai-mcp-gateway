# AI MCP Gateway

一个企业内部使用的 MCP 网关系统。它将业务服务的 HTTP API 配置化转换为 MCP 工具，也可以代理已有的远程 MCP 服务，并统一提供工具发现、Agent 权限、调用校验、服务发现、请求路由和审计能力。

第一阶段采用 Vue 3 独立前端和单个 Spring Boot 后端，使用 MySQL、Redis 与 Nacos，优先保证本地端到端运行。Agent 只连接一个 MCP 地址，并且只能发现和调用角色允许的工具。

## 项目文档

- [系统需求](docs/requirements/mcp-gateway-requirements.md)
- [领域词汇](CONTEXT.md)
- [架构决策](docs/adr/)
- [目标模式执行文档](docs/goals/README.md)
- [云端开发与验收工作流](docs/deployment/cloud-development.md)
- [Agent 协作规则](AGENTS.md)

## 推荐开发方式

当开发者电脑没有容器运行时时，MySQL、Redis 和 Nacos 运行在云服务器 Docker 中；后端和前端仍可在本机运行，以保留热更新和断点调试能力。本机后端默认启用 `local` Profile，通过 SSH 隧道连接云端映射端口：MySQL `16033`、Redis `19736`、Nacos `18848/19848/19849`。

每个 Issue 开发和自动化验证完成后，使用 `compose.cloud.yaml` 将前端、后端和基础设施整体部署到云服务器。云端后端启用 `cloud` Profile，通过 Compose 服务名连接容器内部端口，不经过宿主机映射端口。完整操作参见[云端开发与验收工作流](docs/deployment/cloud-development.md)。

## 可选：本地完整启动

### 环境要求

- Java 21
- Maven 3.6.3 或更高版本
- Node.js 20.19+ 或 22.12+
- Docker 与 Docker Compose

本机具备 Docker 时仍可使用以下兼容方式。命令均在仓库根目录执行，首次启动会创建空的 MySQL 数据卷，后端启动时由 Flyway 自动执行基线迁移。

```powershell
docker compose up -d mysql redis nacos
docker compose ps
```

`docker compose ps` 显示三个依赖均为 `healthy` 后，启动单体后端：

```powershell
cd backend
$env:SPRING_PROFILES_ACTIVE='standalone'
mvn spring-boot:run
```

后端保持运行，另开终端启动 Vue 控制台：

```powershell
cd frontend
npm ci
npm run dev
```

浏览器访问 `http://localhost:5173`。本地固定账号如下：

- 平台管理员：`admin / 666666`，可管理网关基本信息、Agent、角色、工具集和授权关系。
- 审计查看者：`auditor / 666666`，只能查看；写入入口不会显示，直接调用任何管理写 API 都会得到 `403 FORBIDDEN`。

前端通过 Vite 代理调用同一个公开 REST API，不包含独立后端逻辑。控制面源码位于后端 `management` 包；MCP 运行时边界位于 `runtime` 包。当前只提供权限管理和权限结果，不包含 `/mcp`、工具发现或工具调用。

### Agent 权限管理

控制台可以完成以下关键流程：

- 创建、查看、修改和删除 Agent、角色与工具集。
- 为 Agent 授予或撤销角色，为角色关联或撤销工具集。
- 查看 Agent 当前允许的稳定工具名称并集；没有明确关联时结果为空。
- 创建 Agent 或重置凭据时查看一次 API Key。关闭提示后，后续查询只返回非秘密前缀，不再返回明文。

对应的公开管理 REST API 位于：

- `/api/management/agents`，包括 API Key 重置、角色关联和 `/permissions` 权限结果。
- `/api/management/roles`，包括工具集关联。
- `/api/management/tool-sets`，成员以 `<服务标识>.<工具标识>` 的明确名称列表保存。

每个 API Key 由 256 位安全随机数生成。数据库只保存 `BINARY(32)` SHA-256 摘要和可识别 Agent 的短前缀；认证摘要使用恒定时间比较。重置在数据库事务中直接替换摘要，因此旧 Key 立即失效。应用不会记录或审计 API Key 明文。

Compose 暴露的 MySQL、Redis 和 Nacos 端口均只绑定 `127.0.0.1`。Nacos 默认使用官方 `nacos/nacos-server:v2.5.3` 镜像；受限网络环境可以通过 `NACOS_IMAGE` 覆盖为经过核验的兼容镜像，而不修改仓库默认值。

本次隔离云端验收会在验收主机上以独立 Compose 项目运行 MySQL、Redis 和 Nacos，三项依赖仍只监听该主机的 `127.0.0.1`；Nacos 可通过 `NACOS_IMAGE` 使用经过核验的镜像来源。后端连接同一主机的依赖，不使用开发者电脑上的 MySQL、Redis 或 SSH 隧道。该拓扑只用于验收，不改变上述仓库标准本地启动方式。

### 健康检查

- 应用整体：`http://localhost:8080/actuator/health`
- 存活状态：`http://localhost:8080/actuator/health/liveness`
- 就绪状态：`http://localhost:8080/actuator/health/readiness`

整体健康响应的 `components` 分别包含 `db`、`redis` 和 `nacos`。就绪状态要求应用、MySQL、Redis 与 Nacos 均正常，任一核心依赖异常时返回 `DOWN`。

### Flyway 重启验证

首次在空数据卷启动后端时，日志会显示执行 `V1__create_gateway_profile.sql` 和 `V2__create_agent_authorization.sql`。停止后端并再次执行 `mvn spring-boot:run`，Flyway 会识别已迁移数据库，不重复建表且应用应正常启动。不要手工修改 `flyway_schema_history`。

### 自动化验证

后端集成测试使用 H2 的 MySQL 兼容模式执行同一份 Flyway 迁移，覆盖 API Key 一次展示与摘要存储、错误 Key、重置失效、默认拒绝、多角色并集、撤销授权、工具集固定成员、审计查看者只读、迁移重复执行和健康端点：

```powershell
cd backend
mvn test
mvn package
```

如有专用的空 MySQL 验证库，可以执行真实 MySQL 数据类型接缝测试。该测试会运行 Flyway 并写入随机命名的测试 Agent，不要指向生产库：

```powershell
$env:MYSQL_IT_URL='jdbc:mysql://127.0.0.1:3306/mcp_gateway_test?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC'
$env:MYSQL_IT_USERNAME='mcp_gateway'
$env:MYSQL_IT_PASSWORD='mcp_gateway'
mvn -Dtest=MySqlAgentAuthorizationIntegrationTest test
```

该测试直接验证 MySQL `BINARY(32)` 摘要的创建、认证、重置失效和 Agent 名称唯一冲突语义；未设置 `MYSQL_IT_URL` 时自动跳过。

前端测试覆盖登录、平台管理员写入入口、Agent API Key 一次展示和审计查看者只读状态：

```powershell
cd frontend
npm ci
npm test
npm run typecheck
npm run build
```
