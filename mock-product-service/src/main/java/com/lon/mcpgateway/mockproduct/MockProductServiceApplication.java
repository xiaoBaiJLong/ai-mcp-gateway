package com.lon.mcpgateway.mockproduct;

import com.lon.mcpgateway.mockproduct.config.MockProductProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(MockProductProperties.class)
public class MockProductServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(MockProductServiceApplication.class, args);
    }
}
