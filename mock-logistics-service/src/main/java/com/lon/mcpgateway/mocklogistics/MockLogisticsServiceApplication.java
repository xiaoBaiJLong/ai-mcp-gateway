package com.lon.mcpgateway.mocklogistics;

import com.lon.mcpgateway.mocklogistics.config.MockLogisticsProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(MockLogisticsProperties.class)
public class MockLogisticsServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(MockLogisticsServiceApplication.class, args);
    }
}
