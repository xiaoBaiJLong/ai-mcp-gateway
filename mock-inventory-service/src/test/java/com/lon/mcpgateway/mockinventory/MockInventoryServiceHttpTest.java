package com.lon.mcpgateway.mockinventory;

import java.util.List;
import java.util.concurrent.CompletableFuture;
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
        "mock.inventory.slow-response-delay=PT0.05S"
})
class MockInventoryServiceHttpTest {
    @Autowired private WebTestClient client;

    @Test
    void reservesAndReleasesInventoryAtomically() {
        client.post().uri("/api/inventory/reservations").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"orderId\":\"o-100\",\"productId\":\"p-100\",\"warehouseId\":\"wh-100\",\"quantity\":3}")
                .exchange().expectStatus().isCreated().expectBody().jsonPath("$.id").isEqualTo("r-101");
        client.get().uri("/api/inventory/p-100?warehouseId=wh-100").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.available").isEqualTo(97).jsonPath("$.reserved").isEqualTo(3);
        client.post().uri("/api/inventory/reservations/r-101/release").exchange().expectStatus().isOk();
        client.post().uri("/api/inventory/reservations/r-101/release").exchange().expectStatus().isEqualTo(409);
        client.get().uri("/api/inventory/p-100?warehouseId=wh-100").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.available").isEqualTo(100).jsonPath("$.reserved").isEqualTo(0);
    }

    @Test
    void rejectsInsufficientInventoryAndPublishesOpenApi() {
        client.post().uri("/api/inventory/reservations").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"orderId\":\"o-200\",\"productId\":\"p-200\",\"warehouseId\":\"wh-100\",\"quantity\":201}")
                .exchange().expectStatus().isEqualTo(409).expectBody().jsonPath("$.code").isEqualTo("INSUFFICIENT_INVENTORY");
        client.get().uri("/v3/api-docs").exchange().expectStatus().isOk().expectBody()
                .jsonPath("$.paths['/api/inventory/{productId}'].get").exists()
                .jsonPath("$.paths['/api/inventory/reservations'].post").exists()
                .jsonPath("$.paths['/api/inventory/reservations/{reservationId}/release'].post").exists();
        client.get().uri("/api/inventory/server-error").exchange().expectStatus().isEqualTo(500);
    }

    @Test
    void concurrentReservationsCannotMakeInventoryNegative() {
        CompletableFuture<Integer> first = reserveAsync(120);
        CompletableFuture<Integer> second = reserveAsync(120);
        List<Integer> statuses = List.of(first.join(), second.join());
        Assertions.assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        client.get().uri("/api/inventory/p-200?warehouseId=wh-100").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.available").isEqualTo(80).jsonPath("$.reserved").isEqualTo(120);
    }

    private CompletableFuture<Integer> reserveAsync(int quantity) {
        return CompletableFuture.supplyAsync(() -> client.post().uri("/api/inventory/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"orderId\":\"o-concurrent\",\"productId\":\"p-200\",\"warehouseId\":\"wh-100\",\"quantity\":" + quantity + "}")
                .exchange().returnResult(Void.class).getStatus().value());
    }
}
