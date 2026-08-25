# AI MCP Gateway

一个企业内部使用的 MCP 网关系统。它将业务服务的 HTTP API 配置化转换为 MCP 工具，也可以代理已有的远程 MCP 服务，并统一提供工具发现、Agent 权限、调用校验、服务发现、请求路由和审计能力。

第一阶段采用 Vue 3 独立前端和单个 Spring Boot 后端，使用 MySQL、Redis 与 Nacos，优先保证本地端到端运行。Agent 只连接一个 MCP 地址，并且只能发现和调用角色允许的工具。

## 项目文档

- [系统需求](docs/requirements/mcp-gateway-requirements.md)
- [领域词汇](CONTEXT.md)
- [架构决策](docs/adr/)
- [目标模式执行文档](docs/goals/README.md)
- [Agent 协作规则](AGENTS.md)

## 本地启动

### 环境要求

- Java 21
- Maven 3.6.3 或更高版本
- Node.js 20.19+ 或 22.12+
- Docker 与 Docker Compose

以下命令均在仓库根目录执行。首次启动会创建空的 MySQL 数据卷，后端启动时由 Flyway 自动执行基线迁移。

```powershell
docker compose up -d mysql redis nacos
docker compose ps
```

`docker compose ps` 显示三个依赖均为 `healthy` 后，启动单体后端：

```powershell
cd backend
mvn spring-boot:run
```

后端保持运行，另开终端启动 Vue 控制台：

```powershell
cd frontend
npm ci
npm run dev
```

浏览器访问 `http://localhost:5173`。本地固定账号如下：

- 平台管理员：`admin / 666666`，可查看并修改网关基本信息。
- 审计查看者：`auditor / 666666`，只能查看；保存入口不会显示，直接调用写 API 会得到 `403 FORBIDDEN`。

前端通过 Vite 代理调用同一个公开 REST API，不包含独立后端逻辑。控制面源码位于后端 `management` 包；MCP 运行时边界位于 `runtime` 包，本 ticket 不包含工具或 Agent 能力。

Compose 暴露的 MySQL、Redis 和 Nacos 端口均只绑定 `127.0.0.1`。Nacos 默认使用官方 `nacos/nacos-server:v2.5.3` 镜像；受限网络环境可以通过 `NACOS_IMAGE` 覆盖为经过核验的兼容镜像，而不修改仓库默认值。

本次隔离云端验收会在验收主机上以独立 Compose 项目运行 MySQL、Redis 和 Nacos，三项依赖仍只监听该主机的 `127.0.0.1`；Nacos 可通过 `NACOS_IMAGE` 使用经过核验的镜像来源。后端连接同一主机的依赖，不使用开发者电脑上的 MySQL、Redis 或 SSH 隧道。该拓扑只用于验收，不改变上述仓库标准本地启动方式。

### 健康检查

- 应用整体：`http://localhost:8080/actuator/health`
- 存活状态：`http://localhost:8080/actuator/health/liveness`
- 就绪状态：`http://localhost:8080/actuator/health/readiness`

整体健康响应的 `components` 分别包含 `db`、`redis` 和 `nacos`。就绪状态要求应用、MySQL、Redis 与 Nacos 均正常，任一核心依赖异常时返回 `DOWN`。

### Flyway 重启验证

首次在空数据卷启动后端时，日志会显示执行 `V1__create_gateway_profile.sql`。停止后端并再次执行 `mvn spring-boot:run`，Flyway 会识别已迁移数据库，不重复建表且应用应正常启动。不要手工修改 `flyway_schema_history`。

### 自动化验证

后端集成测试使用 H2 的 MySQL 兼容模式执行同一份 Flyway 迁移，覆盖双角色登录、错误凭证、管理员写入、审计查看者越权与状态不变、迁移重复执行和健康端点：

```powershell
cd backend
mvn test
mvn package
```

前端测试覆盖登录、平台管理员写入入口和审计查看者只读状态：

```powershell
cd frontend
npm ci
npm test
npm run typecheck
npm run build
```
