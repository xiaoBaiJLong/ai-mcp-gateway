package com.lon.mcpgateway.mockuser.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mock.user")
public class MockUserProperties {

    private Duration slowResponseDelay = Duration.ofSeconds(16);

    public Duration getSlowResponseDelay() {
        return slowResponseDelay;
    }

    public void setSlowResponseDelay(Duration slowResponseDelay) {
        this.slowResponseDelay = slowResponseDelay;
    }
}
