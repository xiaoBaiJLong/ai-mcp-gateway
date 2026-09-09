package com.lon.mcpgateway.mockpayment.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mock.payment")
public class MockPaymentProperties {
    private Duration slowResponseDelay = Duration.ofSeconds(16);
    public Duration getSlowResponseDelay() { return slowResponseDelay; }
    public void setSlowResponseDelay(Duration slowResponseDelay) { this.slowResponseDelay = slowResponseDelay; }
}
