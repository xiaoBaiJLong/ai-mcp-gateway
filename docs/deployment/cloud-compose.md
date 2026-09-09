# 云端 Docker Compose 部署

本说明只部署 MCP Gateway、管理端、模拟业务服务和验证台；它不会覆盖 Nacos 配置，也不会自动改动 MySQL 结构。MySQL 与 Nacos 是长期基础设施，应用更新只能重建应用组。

## 首次部署

1. 在云主机检出目标提交，复制 `config/cloud.env.example` 为 `config/cloud.env`，并填写其中的密码、Nacos 节点认证值与 `OPENAI_API_KEY`。不要提交该文件。
2. 仅首次启动持久化基础设施：

   ```bash
   docker compose --env-file config/cloud.env up -d mysql nacos
   docker compose --env-file config/cloud.env ps
   ```

3. 在 MySQL 健康后，手动应用当前结构快照；首次部署时此步骤不会清空已有数据：

   ```bash
   docker compose --env-file config/cloud.env exec -T mysql sh -c 'mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"' < mcp-gateway-server/src/main/resources/schema.sql
   ```

   演示环境需要用最新结构覆盖旧结构时，可先只删除本项目的七张业务表，再重新导入结构。该命令会清空这些表中的全部数据，不会删除数据库、MySQL 卷或 Nacos 数据：

   ```bash
   docker compose --env-file config/cloud.env exec -T mysql sh -c 'mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE" -e "SET FOREIGN_KEY_CHECKS=0; DROP TABLE IF EXISTS tool_collection_members, tool_collections, agent_tool_assignments, agent_credentials, agents, http_mappings, mcp_tools; SET FOREIGN_KEY_CHECKS=1;"'
   docker compose --env-file config/cloud.env exec -T mysql sh -c 'mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"' < mcp-gateway-server/src/main/resources/schema.sql
   ```

4. `config/cloud.env` 中的 `GATEWAY_VALIDATION_MODEL` 与 `GATEWAY_VALIDATION_BASE_URL` 提供验证台启动默认值。需要集中管理时，可在 Nacos 的该 Namespace 与 Group 中维护 `mcp-gateway-server.yaml` 的 `gateway.validation.model`、`gateway.validation.base-url`；Nacos 值优先，普通部署绝不导入、覆盖或删除该配置。
5. 构建并启动应用组：

   ```bash
   docker compose --env-file config/cloud.env up -d --build mcp-gateway-server mock-user-service mock-order-service mock-product-service mock-inventory-service mock-payment-service mock-logistics-service web-admin
   docker compose --env-file config/cloud.env ps
   curl -f http://127.0.0.1:18080/
   curl -f http://127.0.0.1/
   ```

容器内 Nginx 绑定云主机回环 `18080`，云主机已有的边缘 Nginx 在 HTTP 80 端口提供公网入口并转发到它；容器内 Nginx 再反向代理 `/api/**` 到网关管理 API、`/mcp` 到 MCP Streamable HTTP Endpoint。MySQL 只监听云主机回环 `3306`；Nacos 只监听回环 `18848` 和 `19848`，可继续按 `docs/agents/cloud-development.md` 建立 SSH 隧道。当前网关没有实现 Nacos 登录，因此 Nacos 以 Docker 内网和宿主机回环端口作为访问边界。

## 应用更新与回滚

更新时只操作应用组，必须带 `--no-deps`，以避免 Compose 触碰 MySQL 和 Nacos：

```bash
git pull --ff-only
docker compose --env-file config/cloud.env up -d --build --no-deps mcp-gateway-server mock-user-service mock-order-service mock-product-service mock-inventory-service mock-payment-service mock-logistics-service web-admin
docker compose --env-file config/cloud.env ps
curl -f http://127.0.0.1:18080/
curl -f http://127.0.0.1/
```

回滚到已验证提交时，检出该提交后重复同一条 `docker compose --env-file config/cloud.env up -d --build --no-deps ...` 命令。不要使用 `docker compose down`，更不要加 `-v`；它会停止基础设施，`-v` 还会删除持久卷。

## 模拟业务服务

应用组包含用户、订单、商品、库存、支付和物流六个模拟业务服务。各服务独立注册到 Nacos，并从固定 `/v3/api-docs` 发布 OpenAPI 3 文档；服务内的演示数据保存在内存中，容器重启后恢复预置状态。订单、商品、库存、支付、物流服务分别监听容器端口 `8082` 到 `8086`，只在 Compose 网络内暴露。

所有模拟服务都使用保留值触发故障：查询资源 ID 或主要写入关联 ID 为 `not-found` 时返回 404，为 `server-error` 时返回 500，为 `slow` 时在默认 16 秒后返回。慢响应时长可通过 `config/cloud.env` 中相应的 `MOCK_*_SLOW_RESPONSE_DELAY` 覆盖。

## 首版复验

云端管理端可依次重复：发现 `mock-user-service` 并导入 Tool、创建智能体并只保存一次 Agent Key、配置工具集与智能体工具快照、用该 Key 访问 `POST /mcp` 的 `tools/list` 和 `tools/call`、更新或禁用 Tool，并在验证台完成真实连接与对话调用。验证 `tools/call` 时覆盖成功、无效 Key、缺少或错误参数、模拟服务的 404/500、`userId=slow` 触发的 15 秒超时，以及禁用 Tool 的 JSON-RPC `-32602` 拒绝。

在共享 Nacos 上进行云端复验前，停止全部本机模拟服务；本机和云端不能同时注册任何同名模拟业务服务。复验完成后如需继续本机开发，先停止云端的六个模拟服务，或改用隔离的 Namespace。
