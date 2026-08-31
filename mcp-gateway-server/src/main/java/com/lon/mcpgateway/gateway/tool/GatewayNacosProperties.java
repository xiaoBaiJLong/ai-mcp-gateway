package com.lon.mcpgateway.gateway.tool;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("gateway.nacos")
public record GatewayNacosProperties(String serverAddr, String namespace, String group) {
}
