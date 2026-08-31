package com.lon.mcpgateway.gateway.app.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("gateway.nacos")
public record GatewayNacosProperties(String serverAddr, String namespace, String group) {
}
