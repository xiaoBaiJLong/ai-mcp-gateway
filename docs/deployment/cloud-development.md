# 云端开发与验收工作流

本项目在开发者电脑没有容器运行时的情况下，使用云服务器 Docker 提供 MySQL、Redis、Nacos，并在每个 Issue 完成后将完整应用部署到同一台云服务器供验收。

## 配置边界

| 运行方式 | Spring Profile | MySQL | Redis | Nacos |
| --- | --- | --- | --- | --- |
| 本机运行后端 | `local`，也是默认 Profile | 云服务器或 SSH 隧道的 `16033` | 云服务器或 SSH 隧道的 `19736` | SSH 隧道的 `18848`，并同时转发派生的 gRPC 端口 |
| 云端 Compose 运行后端 | `cloud` | Compose 服务名 `mysql:3306` | Compose 服务名 `redis:6379` | Compose 服务名 `nacos:8848` |
| 可选的本机完整 Compose | `standalone` | `127.0.0.1:3306` | `127.0.0.1:6379` | `127.0.0.1:8848` |
| 自动化测试 | `test` | H2 MySQL 兼容模式 | 禁用 Redis 健康检查 | 禁用 Nacos |

`local` 和 `cloud` 只改变基础设施连接拓扑，数据库名称及凭据仍由环境变量提供。任何真实密码都不得提交到仓库。

## 第一次准备云服务器

服务器需要安装 Git、Docker Engine 和 Docker Compose v2。将仓库检出到固定目录，例如：

```bash
git clone <仓库地址> /opt/ai-mcp-gateway
cd /opt/ai-mcp-gateway
cp .env.cloud.example .env.cloud
chmod 600 .env.cloud
```

编辑 `.env.cloud`，至少替换 MySQL、MySQL root 和 Redis 的三个示例密码。默认端口如下：

- MySQL：服务器 `127.0.0.1:16033` 映射到容器 `3306`。
- Redis：服务器 `127.0.0.1:19736` 映射到容器 `6379`。
- Nacos：服务器 `127.0.0.1:18848`、`19848`、`19849` 映射到容器协议端口。
- Web 验收入口：服务器 `0.0.0.0:18080` 映射到前端容器 `80`。

先启动基础设施：

```bash
docker compose --env-file .env.cloud -f compose.cloud.yaml up -d mysql redis nacos --wait
```

## 本机运行应用

推荐保留数据库端口的回环绑定，并通过 SSH 隧道访问：

```powershell
ssh -N `
  -L 16033:127.0.0.1:16033 `
  -L 19736:127.0.0.1:19736 `
  -L 18848:127.0.0.1:18848 `
  -L 19848:127.0.0.1:19848 `
  -L 19849:127.0.0.1:19849 `
  mcp-dev
```

另开 PowerShell，设置本机进程使用的凭据：

```powershell
$env:SPRING_PROFILES_ACTIVE='local'
$env:MYSQL_PASSWORD='<与云端 .env.cloud 一致的开发库密码>'
$env:REDIS_PASSWORD='<与云端 .env.cloud 一致的 Redis 密码>'

cd backend
mvn spring-boot:run
```

`local` Profile 默认使用 `127.0.0.1:16033`、`127.0.0.1:19736` 和 `127.0.0.1:18848`。如果 MySQL、Redis 通过受控私网直接访问，可以额外设置完整的 `MYSQL_URL` 和 `REDIS_HOST`。

只修改公网端口不能形成安全边界。若确需直接监听公网地址，应分别修改 `MYSQL_BIND_ADDRESS` 或 `REDIS_BIND_ADDRESS`，同时使用云防火墙仅允许固定开发者 IP；Redis 当前没有配置传输层加密，因此仍优先使用 SSH 隧道。Nacos 保持回环绑定。

## 每个 Issue 完成后的部署

Issue 完成并通过本地门禁后，按以下顺序操作：

1. 将已验证的提交同步到云服务器当前跟踪分支。
2. 在云服务器执行部署脚本。
3. 确认所有 Compose 服务为 `healthy`。
4. 访问验收地址并执行该 Issue 的验收步骤。
5. 记录脚本输出的 Git 提交号，确保验收结果对应确定版本。

云服务器命令：

```bash
cd /opt/ai-mcp-gateway
sh scripts/deploy-cloud.sh /opt/ai-mcp-gateway
```

脚本只允许 `git pull --ff-only`，不会用远端内容强制覆盖服务器上的分叉提交。若代码已经通过其他受控方式同步到服务器，可以执行：

```bash
SKIP_GIT_PULL=true sh scripts/deploy-cloud.sh /opt/ai-mcp-gateway
```

默认验收地址和健康检查地址为：

- 控制台：`http://<云服务器地址>:18080`
- 就绪状态：`http://<云服务器地址>:18080/actuator/health/readiness`

云防火墙应只向验收人员来源 IP 开放 `APP_PORT`。正式对外使用前还需要增加域名、HTTPS 和更严格的控制面身份认证。

## 数据与回滚边界

- `docker compose up` 保留 MySQL、Redis 命名卷，部署新代码不会清空数据。
- Flyway 随后端启动自动迁移数据库，不要手工修改 `flyway_schema_history`。
- 开发环境和生产环境不能共用数据库、Redis 实例或密码。
- 数据库迁移通常不能仅靠回退镜像撤销；涉及不兼容迁移的 Issue 必须提前设计向前兼容的回滚方案。
- 需要隔离测试数据时，使用独立的 Compose 项目名和独立卷，不要在验收库执行清库测试。
