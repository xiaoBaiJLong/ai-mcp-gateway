package com.lon.mcpgateway.gateway.api.validation;

public interface ValidationModelSettingsPort {
    ModelSettings settings();

    record ModelSettings(String model, String baseUrl) {
    }
}
