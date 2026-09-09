package com.lon.mcpgateway.mockorder;

import java.time.Duration;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.cloud.nacos.discovery.enabled=false", "spring.cloud.nacos.discovery.server-addr=127.0.0.1:8848",
        "spring.cloud.nacos.discovery.namespace=test", "spring.cloud.nacos.discovery.group=DEFAULT_GROUP",
        "mock.order.slow-response-delay=PT0.05S"
})
class MockOrderServiceHttpTest {
    @Autowired private WebTestClient client;

    @Test
    void createsReadsAndTransitionsOrder() {
        client.post().uri("/api/orders").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"userId\":\"u-100\",\"items\":[{\"productId\":\"p-100\",\"quantity\":2,\"unitPrice\":199.00}]}")
                .exchange().expectStatus().isCreated().expectBody().jsonPath("$.id").isEqualTo("o-101")
                .jsonPath("$.totalAmount").isEqualTo(398.0);
        client.patch().uri("/api/orders/o-101/status").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"status\":\"PAID\"}").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("PAID");
        client.get().uri("/api/orders/o-101").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("PAID");
    }

    @Test
    void rejectsInvalidTransitionAndInvalidRequest() {
        client.patch().uri("/api/orders/o-100/status").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"status\":\"PAID\"}").exchange().expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.code").isEqualTo("INVALID_ORDER_TRANSITION");
        client.post().uri("/api/orders").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"userId\":\"\",\"items\":[]}").exchange().expectStatus().isBadRequest();
    }

    @Test
    void publishesOpenApiAndSupportsFailureScenarios() {
        client.get().uri("/v3/api-docs").exchange().expectStatus().isOk().expectBody()
                .jsonPath("$.paths['/api/orders/{orderId}'].get").exists()
                .jsonPath("$.paths['/api/orders'].post").exists()
                .jsonPath("$.paths['/api/orders/{orderId}/status'].patch").exists();
        client.get().uri("/api/orders/not-found").exchange().expectStatus().isNotFound();
        client.get().uri("/api/orders/server-error").exchange().expectStatus().isEqualTo(500);
        long started = System.nanoTime();
        client.get().uri("/api/orders/slow").exchange().expectStatus().isOk();
        Assertions.assertThat(Duration.ofNanos(System.nanoTime() - started)).isGreaterThanOrEqualTo(Duration.ofMillis(40));
    }
}
