# 远程网关与单一 Nacos 范围

网关是通过固定 Endpoint 访问的单一远程 MCP Server，且绝不作为 Nacos 服务实例注册。它只连接一个固定的 Nacos 集群、Namespace 与 Group 来发现下游业务服务；首版不支持多集群切换或本地 Studio。
