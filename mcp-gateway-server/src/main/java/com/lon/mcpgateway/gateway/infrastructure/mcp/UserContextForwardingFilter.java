package com.lon.mcpgateway.gateway.infrastructure.mcp;

import com.lon.mcpgateway.gateway.types.mcp.McpRuntimeModels.UserContext;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import reactor.core.publisher.Mono;

final class UserContextForwardingFilter implements ExchangeFilterFunction {
    static final String USER_CONTEXT_ATTRIBUTE = UserContextForwardingFilter.class.getName() + ".userContext";

    @Override
    public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
        Object attribute = request.attributes().get(USER_CONTEXT_ATTRIBUTE);
        if (!(attribute instanceof UserContext userContext)) {
            return next.exchange(request);
        }
        ClientRequest forwarded = ClientRequest.from(request).headers(headers -> {
            if (userContext.userId() != null) {
                headers.set("X-User-Id", userContext.userId());
            }
            if (userContext.tenantId() != null) {
                headers.set("X-Tenant-Id", userContext.tenantId());
            }
        }).build();
        return next.exchange(forwarded);
    }
}
