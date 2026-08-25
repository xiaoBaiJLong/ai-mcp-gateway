package com.xiaobaijlong.mcpgateway.runtime.nacos;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NacosClientConfiguration {

    @Bean(destroyMethod = "shutDown")
    @ConditionalOnProperty(name = "gateway.nacos.enabled", havingValue = "true", matchIfMissing = true)
    NamingService namingService(@Value("${gateway.nacos.server-address}") String serverAddress)
            throws NacosException {
        return NacosFactory.createNamingService(serverAddress);
    }
}
