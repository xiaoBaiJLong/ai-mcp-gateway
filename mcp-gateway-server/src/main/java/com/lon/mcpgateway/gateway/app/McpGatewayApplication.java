package com.lon.mcpgateway.gateway.app;

import org.springframework.context.annotation.ComponentScan;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
@ComponentScan("com.lon.mcpgateway.gateway")
@MapperScan(basePackages = "com.lon.mcpgateway.gateway.infrastructure", annotationClass = Mapper.class)
public class McpGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(McpGatewayApplication.class, args);
    }
}
