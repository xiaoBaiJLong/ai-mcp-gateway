package com.lon.mcpgateway.gateway.infrastructure.openapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.lon.mcpgateway.gateway.api.discovery.BusinessServiceDiscoveryPort;
import com.lon.mcpgateway.gateway.api.discovery.OpenApiDocumentPort;
import com.lon.mcpgateway.gateway.app.config.GatewayOpenApiProperties;
import com.lon.mcpgateway.gateway.types.common.OpenApiImportException;
import java.time.Duration;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
class HttpOpenApiDocumentClient implements OpenApiDocumentPort {
    private final BusinessServiceDiscoveryPort discovery;
    private final WebClient webClient;
    private final Duration timeout;

    HttpOpenApiDocumentClient(BusinessServiceDiscoveryPort discovery, WebClient.Builder webClientBuilder,
            GatewayOpenApiProperties properties) {
        this.discovery = discovery;
        this.webClient = webClientBuilder.build();
        this.timeout = properties.timeout();
    }

    @Override
    public JsonNode fetch(String serviceName) {
        BusinessServiceDiscoveryPort.ServiceAddress instance = discovery.findHealthyInstances(serviceName).stream()
                .findFirst().orElseThrow(() -> new OpenApiImportException("业务服务没有健康实例"));
        String scheme = instance.secure() ? "https" : "http";
        try {
            JsonNode document = webClient.get().uri(scheme + "://" + instance.host() + ":" + instance.port() + "/v3/api-docs")
                    .retrieve().bodyToMono(JsonNode.class).block(timeout);
            if (document == null) {
                throw new OpenApiImportException("OpenAPI 文档为空");
            }
            return document;
        } catch (OpenApiImportException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new OpenApiImportException("获取 OpenAPI 文档失败", exception);
        }
    }
}
