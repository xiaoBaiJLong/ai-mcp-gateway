package com.lon.mcpgateway.mockorder;

import com.lon.mcpgateway.mockorder.config.MockOrderProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(MockOrderProperties.class)
public class MockOrderServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(MockOrderServiceApplication.class, args);
    }
}
