package com.lon.mcpgateway.gateway.infrastructure.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.lon.mcpgateway.gateway.api.validation.ValidationModelSettingsPort;
import com.lon.mcpgateway.gateway.app.config.GatewayNacosProperties;
import com.lon.mcpgateway.gateway.types.common.GatewayException;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
class NacosValidationModelSettingsClient implements ValidationModelSettingsPort {
    private final WebClient webClient;
    private final GatewayNacosProperties nacos;
    private final String dataId;
    private final ModelSettings environmentSettings;
    private final YAMLMapper yamlMapper = new YAMLMapper();

    NacosValidationModelSettingsClient(WebClient.Builder webClientBuilder, GatewayNacosProperties nacos,
            @Value("${gateway.validation.nacos-data-id:mcp-gateway-server.yaml}") String dataId,
            @Value("${gateway.validation.model:deepseek-v4-flash}") String model,
            @Value("${gateway.validation.base-url:https://api.deepseek.com}") String baseUrl) {
        String address = nacos.serverAddr().split(",")[0].trim();
        this.webClient = webClientBuilder.baseUrl(address.startsWith("http") ? address : "http://" + address).build();
        this.nacos = nacos;
        this.dataId = dataId;
        this.environmentSettings = new ModelSettings(model, baseUrl);
    }

    @Override
    public ModelSettings settings() {
        try {
            String content = webClient.get().uri(builder -> builder.path("/nacos/v1/cs/configs")
                    .queryParam("dataId", dataId).queryParam("group", nacos.group()).queryParam("tenant", nacos.namespace()).build())
                    .retrieve().bodyToMono(String.class).block(Duration.ofSeconds(5));
            JsonNode root = yamlMapper.readTree(content);
            JsonNode validation = root.path("gateway").path("validation");
            String model = validation.path("model").asText(environmentSettings.model());
            String baseUrl = validation.path("base-url")
                    .asText(validation.path("baseUrl").asText(environmentSettings.baseUrl()));
            if (model.isBlank() || baseUrl.isBlank()) {
                throw new GatewayException("VALIDATION_MODEL_UNAVAILABLE", "未配置验证模型名称或 Base URL");
            }
            return new ModelSettings(model, baseUrl);
        } catch (GatewayException exception) {
            throw exception;
        } catch (Exception exception) {
            if (environmentSettings.model().isBlank() || environmentSettings.baseUrl().isBlank()) {
                throw new GatewayException("VALIDATION_MODEL_UNAVAILABLE", "无法从 Nacos 读取验证模型配置，且环境默认配置不完整");
            }
            return environmentSettings;
        }
    }
}
