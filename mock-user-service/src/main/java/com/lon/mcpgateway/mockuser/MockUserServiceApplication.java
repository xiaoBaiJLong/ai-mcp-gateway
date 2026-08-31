package com.lon.mcpgateway.mockuser;

import com.lon.mcpgateway.mockuser.config.MockUserProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(MockUserProperties.class)
public class MockUserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MockUserServiceApplication.class, args);
    }
}
