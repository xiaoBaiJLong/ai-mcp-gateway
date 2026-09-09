package com.lon.mcpgateway.mockinventory;

import com.lon.mcpgateway.mockinventory.config.MockInventoryProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(MockInventoryProperties.class)
public class MockInventoryServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(MockInventoryServiceApplication.class, args);
    }
}
