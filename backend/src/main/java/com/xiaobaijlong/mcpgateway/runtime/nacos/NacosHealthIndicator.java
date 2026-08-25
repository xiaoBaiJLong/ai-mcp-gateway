package com.xiaobaijlong.mcpgateway.runtime.nacos;

import com.alibaba.nacos.api.naming.NamingService;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

@Component("nacos")
@ConditionalOnBean(NamingService.class)
public class NacosHealthIndicator implements HealthIndicator {

    private final NamingService namingService;

    public NacosHealthIndicator(NamingService namingService) {
        this.namingService = namingService;
    }

    @Override
    public Health health() {
        try {
            String serverStatus = namingService.getServerStatus();
            if ("UP".equalsIgnoreCase(serverStatus)) {
                return Health.up().withDetail("serverStatus", serverStatus).build();
            }
            return Health.down().withDetail("serverStatus", serverStatus).build();
        } catch (RuntimeException exception) {
            return Health.down(exception).build();
        }
    }
}
