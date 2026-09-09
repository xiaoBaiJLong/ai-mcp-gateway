package com.lon.mcpgateway.mocklogistics;

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
        "mock.logistics.slow-response-delay=PT0.05S"
})
class MockLogisticsServiceHttpTest {
    @Autowired private WebTestClient client;

    @Test
    void createsReadsAndTransitionsShipment() {
        client.post().uri("/api/shipments").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"orderId\":\"o-101\",\"carrier\":\"SF\",\"recipientAddress\":\"演示地址\"}")
                .exchange().expectStatus().isCreated().expectBody().jsonPath("$.id").isEqualTo("s-101");
        client.patch().uri("/api/shipments/s-101/status").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"status\":\"PICKED_UP\",\"location\":\"上海网点\"}").exchange().expectStatus().isOk();
        client.get().uri("/api/shipments/s-101").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("PICKED_UP");
        client.patch().uri("/api/shipments/s-101/status").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"status\":\"DELIVERED\",\"location\":\"收件地址\"}").exchange().expectStatus().isEqualTo(409);
    }

    @Test
    void publishesOpenApiAndSupportsFailureScenarios() {
        client.get().uri("/v3/api-docs").exchange().expectStatus().isOk().expectBody()
                .jsonPath("$.paths['/api/shipments/{shipmentId}'].get").exists()
                .jsonPath("$.paths['/api/shipments'].post").exists()
                .jsonPath("$.paths['/api/shipments/{shipmentId}/status'].patch").exists();
        client.get().uri("/api/shipments/not-found").exchange().expectStatus().isNotFound();
        client.get().uri("/api/shipments/server-error").exchange().expectStatus().isEqualTo(500);
    }
}
