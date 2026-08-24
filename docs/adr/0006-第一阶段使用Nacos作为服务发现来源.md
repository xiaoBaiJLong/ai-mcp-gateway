# 第一阶段使用 Nacos 作为服务发现来源

系统虽然部署在 Kubernetes 中，但现有业务服务使用 Spring Cloud Alibaba 生态治理，因此第一阶段连接一个 Nacos 集群和一个 namespace，以 Nacos 作为业务服务实例的注册与发现权威来源，而不直接以 Kubernetes Service 作为权威目录。业务服务直接注册 Nacos，MCP 网关只订阅实例变化，不提供第二套实例注册 API；其他注册中心适配器留待出现真实需求后再实现。
