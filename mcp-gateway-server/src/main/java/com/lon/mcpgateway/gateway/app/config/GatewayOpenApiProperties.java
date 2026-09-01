package com.lon.mcpgateway.gateway.app.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("gateway.openapi")
public record GatewayOpenApiProperties(Duration timeout) {
}
