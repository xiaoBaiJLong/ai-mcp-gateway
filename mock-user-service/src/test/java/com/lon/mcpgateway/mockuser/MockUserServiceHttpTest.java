package com.lon.mcpgateway.mockuser;

import java.time.Duration;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.nacos.discovery.server-addr=127.0.0.1:8848",
        "spring.cloud.nacos.discovery.namespace=test",
        "spring.cloud.nacos.discovery.group=DEFAULT_GROUP",
        "mock.user.slow-response-delay=PT0.05S"
})
class MockUserServiceHttpTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void returnsUserById() {
        webTestClient.get()
                .uri("/api/users/u-100?verbose=true")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo("u-100")
                .jsonPath("$.verbose").isEqualTo(true);
    }

    @Test
    void searchesUsersWithJsonRequestBody() {
        webTestClient.post()
                .uri("/api/users/search")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"keyword\":\"lon\",\"page\":1}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.items[0].id").isEqualTo("u-100");
    }

    @Test
    void publishesOpenApiDocumentForUserEndpoints() {
        webTestClient.get()
                .uri("/v3/api-docs")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.openapi").value(value -> value.toString().startsWith("3."))
                .jsonPath("$.paths['/api/users/{userId}'].get").exists()
                .jsonPath("$.paths['/api/users/search'].post").exists();
    }

    @Test
    void returnsBusinessNotFoundForReservedUserId() {
        webTestClient.get()
                .uri("/api/users/not-found")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo("USER_NOT_FOUND");
    }

    @Test
    void returnsBusinessServerErrorForReservedSearchKeyword() {
        webTestClient.post()
                .uri("/api/users/search")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"keyword\":\"server-error\"}")
                .exchange()
                .expectStatus().isEqualTo(500)
                .expectBody()
                .jsonPath("$.code").isEqualTo("USER_SEARCH_FAILED");
    }

    @Test
    void delaysReservedUserResponseUsingConfiguredDelay() {
        long requestStartedAt = System.nanoTime();

        webTestClient.get()
                .uri("/api/users/slow")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo("slow");

        Duration elapsed = Duration.ofNanos(System.nanoTime() - requestStartedAt);
        Assertions.assertThat(elapsed).isGreaterThanOrEqualTo(Duration.ofMillis(40));
    }
}
