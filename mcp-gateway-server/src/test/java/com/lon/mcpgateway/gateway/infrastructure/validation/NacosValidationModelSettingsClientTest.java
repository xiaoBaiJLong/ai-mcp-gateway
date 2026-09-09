package com.lon.mcpgateway.gateway.infrastructure.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.lon.mcpgateway.gateway.api.validation.ValidationModelSettingsPort.ModelSettings;
import com.lon.mcpgateway.gateway.app.config.GatewayNacosProperties;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

class NacosValidationModelSettingsClientTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void usesEnvironmentSettingsWhenNacosConfigurationIsMissing() throws Exception {
        startServer(404, "");

        ModelSettings settings = client("environment-model", "https://environment.example/v1").settings();

        assertEquals("environment-model", settings.model());
        assertEquals("https://environment.example/v1", settings.baseUrl());
    }

    @Test
    void prefersNacosSettingsWhenConfigurationExists() throws Exception {
        startServer(200, """
                gateway:
                  validation:
                    model: nacos-model
                    base-url: https://nacos.example/v1
                """);

        ModelSettings settings = client("environment-model", "https://environment.example/v1").settings();

        assertEquals("nacos-model", settings.model());
        assertEquals("https://nacos.example/v1", settings.baseUrl());
    }

    private NacosValidationModelSettingsClient client(String model, String baseUrl) {
        GatewayNacosProperties nacos = new GatewayNacosProperties("127.0.0.1:" + server.getAddress().getPort(), "", "DEFAULT_GROUP");
        return new NacosValidationModelSettingsClient(WebClient.builder(), nacos, "mcp-gateway-server.yaml", model, baseUrl);
    }

    private void startServer(int status, String body) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/nacos/v1/cs/configs", exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }
}
