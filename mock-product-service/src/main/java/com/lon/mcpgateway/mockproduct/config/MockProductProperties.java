package com.lon.mcpgateway.mockproduct.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mock.product")
public class MockProductProperties {
    private Duration slowResponseDelay = Duration.ofSeconds(16);
    public Duration getSlowResponseDelay() { return slowResponseDelay; }
    public void setSlowResponseDelay(Duration slowResponseDelay) { this.slowResponseDelay = slowResponseDelay; }
}
