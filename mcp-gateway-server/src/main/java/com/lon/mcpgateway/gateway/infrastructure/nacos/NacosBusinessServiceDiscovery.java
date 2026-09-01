package com.lon.mcpgateway.gateway.infrastructure.nacos;

import com.fasterxml.jackson.databind.JsonNode;
import com.lon.mcpgateway.gateway.api.discovery.BusinessServiceDiscoveryPort;
import com.lon.mcpgateway.gateway.app.config.GatewayNacosProperties;
import com.lon.mcpgateway.gateway.app.config.GatewayOpenApiProperties;
import com.lon.mcpgateway.gateway.types.common.OpenApiImportException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriBuilder;

@Component
class NacosBusinessServiceDiscovery implements BusinessServiceDiscoveryPort {
    private final WebClient webClient;
    private final GatewayNacosProperties properties;
    private final Duration timeout;

    NacosBusinessServiceDiscovery(WebClient.Builder webClientBuilder, GatewayNacosProperties properties,
            GatewayOpenApiProperties openApiProperties) {
        this.webClient = webClientBuilder.baseUrl(baseUrl(properties.serverAddr())).build();
        this.properties = properties;
        this.timeout = openApiProperties.timeout();
    }

    @Override
    public List<String> findHealthyServiceNames() {
        JsonNode response = get(builder -> builder.path("/nacos/v1/ns/service/list")
                .queryParam("pageNo", 1)
                .queryParam("pageSize", 1000)
                .queryParam("groupName", properties.group())
                .queryParam("namespaceId", properties.namespace()).build());
        List<String> healthy = new ArrayList<>();
        for (JsonNode service : response.path("doms")) {
            String serviceName = service.asText();
            String groupedPrefix = properties.group() + "@@";
            if (serviceName.startsWith(groupedPrefix)) {
                serviceName = serviceName.substring(groupedPrefix.length());
            }
            if (!findHealthyInstances(serviceName).isEmpty()) {
                healthy.add(serviceName);
            }
        }
        return healthy;
    }

    @Override
    public List<ServiceAddress> findHealthyInstances(String serviceName) {
        JsonNode response = get(builder -> builder.path("/nacos/v1/ns/instance/list")
                .queryParam("serviceName", serviceName)
                .queryParam("groupName", properties.group())
                .queryParam("namespaceId", properties.namespace())
                .queryParam("healthyOnly", true).build());
        List<ServiceAddress> instances = new ArrayList<>();
        for (JsonNode host : response.path("hosts")) {
            if (host.path("healthy").asBoolean()) {
                instances.add(new ServiceAddress(host.path("ip").asText(), host.path("port").asInt(),
                        host.path("secure").asBoolean(false)));
            }
        }
        return instances;
    }

    private JsonNode get(java.util.function.Function<UriBuilder, URI> uriFactory) {
        try {
            return webClient.get().uri(uriFactory).retrieve().bodyToMono(JsonNode.class).block(timeout);
        } catch (RuntimeException exception) {
            throw new OpenApiImportException("无法从 Nacos 获取健康业务服务", exception);
        }
    }

    private String baseUrl(String serverAddr) {
        String firstAddress = serverAddr.split(",")[0].trim();
        return firstAddress.startsWith("http://") || firstAddress.startsWith("https://")
                ? firstAddress : "http://" + firstAddress;
    }
}
