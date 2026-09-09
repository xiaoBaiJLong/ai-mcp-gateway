package com.lon.mcpgateway.mockpayment;

import com.lon.mcpgateway.mockpayment.config.MockPaymentProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(MockPaymentProperties.class)
public class MockPaymentServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(MockPaymentServiceApplication.class, args);
    }
}
