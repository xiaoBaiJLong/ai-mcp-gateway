# 第一阶段使用 Nacos 作为服务发现来源

系统虽然部署在 Kubernetes 中，但现有业务服务使用 Spring Cloud Alibaba 生态治理，因此第一阶段以 Nacos 作为业务服务实例的注册与发现来源，而不直接以 Kubernetes Service 作为权威目录。MCP 网关订阅 Nacos 的实例变化并用于动态路由，其他注册中心适配器留待出现真实需求后再实现。
