package com.lon.mcpgateway.gateway.infrastructure.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lon.mcpgateway.gateway.api.discovery.BusinessServiceDiscoveryPort;
import com.lon.mcpgateway.gateway.api.mcp.McpToolInvocationPort;
import com.lon.mcpgateway.gateway.types.mcp.McpRuntimeModels.ToolInvocationResult;
import com.lon.mcpgateway.gateway.types.mcp.McpRuntimeModels.UserContext;
import com.lon.mcpgateway.gateway.types.tool.ToolModels.RuntimeToolRecord;
import java.net.URI;
import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

@Component
class HttpMcpToolInvoker implements McpToolInvocationPort {
    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    private final BusinessServiceDiscoveryPort discovery;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final Map<String, AtomicLong> instanceCounters = new ConcurrentHashMap<>();

    HttpMcpToolInvoker(BusinessServiceDiscoveryPort discovery, WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        this.discovery = discovery;
        this.webClient = webClientBuilder.filter(new UserContextForwardingFilter()).build();
        this.objectMapper = objectMapper;
    }

    @Override
    public ToolInvocationResult invoke(RuntimeToolRecord tool, JsonNode arguments, UserContext userContext) {
        try {
            java.util.List<BusinessServiceDiscoveryPort.ServiceAddress> instances = discovery.findHealthyInstances(tool.serviceName());
            BusinessServiceDiscoveryPort.ServiceAddress instance = nextInstance(tool.serviceName(), instances);
            if (instance == null) {
                return result(503, null, "SERVICE_UNAVAILABLE");
            }
            WebClient.RequestBodySpec request = webClient.method(HttpMethod.valueOf(tool.method())).uri(uri(tool, arguments, instance))
                    .attribute(UserContextForwardingFilter.USER_CONTEXT_ATTRIBUTE, userContext)
                    .headers(headers -> headers(headers, arguments));
            JsonNode body = arguments.path("body");
            WebClient.RequestHeadersSpec<?> requestWithBody = body.isMissingNode() ? request : request.contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body);
            ResponseEntity<String> response = requestWithBody.exchangeToMono(clientResponse -> clientResponse.toEntity(String.class))
                    .block(TIMEOUT);
            return response(response.getStatusCode().value(), response.getHeaders().getContentType(), response.getBody());
        } catch (RuntimeException exception) {
            if (causedByTimeout(exception)) {
                return result(504, null, "TIMEOUT");
            }
            return result(502, null, "BAD_GATEWAY");
        }
    }

    private boolean causedByTimeout(Throwable exception) {
        for (Throwable current = exception; current != null; current = current.getCause()) {
            if (current instanceof java.util.concurrent.TimeoutException
                    || current.getMessage() != null && current.getMessage().contains("Timeout on blocking read")) {
                return true;
            }
        }
        return false;
    }

    private BusinessServiceDiscoveryPort.ServiceAddress nextInstance(String serviceName,
            java.util.List<BusinessServiceDiscoveryPort.ServiceAddress> instances) {
        if (instances.isEmpty()) {
            return null;
        }
        long index = instanceCounters.computeIfAbsent(serviceName, ignored -> new AtomicLong()).getAndIncrement();
        return instances.get((int) Math.floorMod(index, instances.size()));
    }

    private URI uri(RuntimeToolRecord tool, JsonNode arguments, BusinessServiceDiscoveryPort.ServiceAddress instance) {
        String path = tool.path();
        Iterator<Map.Entry<String, JsonNode>> values = arguments.path("path").fields();
        while (values.hasNext()) {
            Map.Entry<String, JsonNode> value = values.next();
            path = path.replace("{" + value.getKey() + "}", value.getValue().asText());
        }
        UriComponentsBuilder builder = UriComponentsBuilder.newInstance().scheme(instance.secure() ? "https" : "http")
                .host(instance.host()).port(instance.port()).path(path);
        arguments.path("query").fields().forEachRemaining(value -> builder.queryParam(value.getKey(), value.getValue().asText()));
        return builder.build().encode().toUri();
    }

    private void headers(HttpHeaders headers, JsonNode arguments) {
        arguments.path("headers").fields().forEachRemaining(value -> headers.set(value.getKey(), value.getValue().asText()));
    }

    private ToolInvocationResult response(int status, MediaType contentType, String payload) {
        if (payload == null || payload.isBlank()) {
            return result(status, null, null);
        }
        if (contentType != null && !MediaType.APPLICATION_JSON.isCompatibleWith(contentType)) {
            return result(status, objectMapper.getNodeFactory().textNode(payload), "UNSUPPORTED_RESPONSE_MEDIA_TYPE");
        }
        try {
            return result(status, objectMapper.readTree(payload), null);
        } catch (Exception exception) {
            return result(status, objectMapper.getNodeFactory().textNode(payload), "UNSUPPORTED_RESPONSE_MEDIA_TYPE");
        }
    }

    private ToolInvocationResult result(int status, JsonNode body, String error) {
        return new ToolInvocationResult(status, body == null ? objectMapper.nullNode() : body, error);
    }
}
