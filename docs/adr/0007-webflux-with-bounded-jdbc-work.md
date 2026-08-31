# WebFlux 与有界 JDBC 工作线程

网关使用 WebFlux 承接 MCP 与下游 HTTP 流量，但 MyBatis-Plus 仍通过阻塞 JDBC 访问 MySQL。为避免阻塞 Netty 事件循环，所有数据库工作转移到有界工作线程池；首版不引入缓存，配置读取以数据库当前已提交数据为准。
