# 使用 WebFlux 适配 MCP 2025-11-25

首版只支持 MCP 2025-11-25 的 Streamable HTTP。相较 2026-07-28，选择该版本是因为已发布的 Java MCP SDK 支持它，而新版尚无已发布 Java SDK 支持；网关维持 Spring Boot 3.5 与 WebFlux，通过薄的 WebFlux 传输适配层而不是要求 Spring Boot 4.x 的 Spring AI 2.x 传输层实现协议。
