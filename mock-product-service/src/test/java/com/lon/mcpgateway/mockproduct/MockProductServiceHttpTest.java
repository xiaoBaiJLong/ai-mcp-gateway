package com.lon.mcpgateway.mockproduct;

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
        "mock.product.slow-response-delay=PT0.05S"
})
class MockProductServiceHttpTest {
    @Autowired private WebTestClient client;

    @Test
    void searchesUpdatesAndReadsProduct() {
        client.post().uri("/api/products/search").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"keyword\":\"键盘\",\"page\":1}").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.items[0].id").isEqualTo("p-100");
        client.patch().uri("/api/products/p-100").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"price\":209.00,\"active\":false}").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.price").isEqualTo(209.0).jsonPath("$.active").isEqualTo(false);
        client.get().uri("/api/products/p-100").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.price").isEqualTo(209.0);
    }

    @Test
    void publishesOpenApiAndSupportsFailureScenarios() {
        client.get().uri("/v3/api-docs").exchange().expectStatus().isOk().expectBody()
                .jsonPath("$.paths['/api/products/{productId}'].get").exists()
                .jsonPath("$.paths['/api/products/search'].post").exists()
                .jsonPath("$.paths['/api/products/{productId}'].patch").exists();
        client.get().uri("/api/products/not-found").exchange().expectStatus().isNotFound();
        client.post().uri("/api/products/search").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"keyword\":\"server-error\"}").exchange().expectStatus().isEqualTo(500);
        long started = System.nanoTime();
        client.get().uri("/api/products/slow").exchange().expectStatus().isOk();
        Assertions.assertThat(Duration.ofNanos(System.nanoTime() - started)).isGreaterThanOrEqualTo(Duration.ofMillis(40));
    }
}
