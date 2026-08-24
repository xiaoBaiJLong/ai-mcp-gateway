# 后端统一采用 Java 和 Spring 生态

控制面和数据面统一使用 Java 21、Spring Boot 3.x 与 Spring Cloud Alibaba/Nacos Client，前端使用 Vue 3 和 TypeScript，而不为数据面单独引入 Go。该选择复用现有 Spring Cloud Alibaba 服务治理能力并降低首期多语言维护成本，同时接受 Java 数据面在资源占用和极限性能上可能高于专用 Go 实现。
