package com.xiaobaijlong.mcpgateway.management.authorization;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@EnabledIfEnvironmentVariable(named = "MYSQL_IT_URL", matches = ".+")
class MySqlAgentAuthorizationIntegrationTest {

    @Test
    void binaryDigestSupportsCreateAuthenticateAndResetOnRealMySql() {
        String url = System.getenv("MYSQL_IT_URL");
        String username = System.getenv().getOrDefault("MYSQL_IT_USERNAME", "mcp_gateway");
        String password = System.getenv().getOrDefault("MYSQL_IT_PASSWORD", "mcp_gateway");
        DriverManagerDataSource dataSource = new DriverManagerDataSource(url, username, password);

        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        AuthorizationRepository repository = new AuthorizationRepository(jdbcTemplate);
        AgentApiKeyService apiKeyService = new AgentApiKeyService(repository);
        AuthorizationService service = new AuthorizationService(repository, apiKeyService);

        String name = "mysql-agent-" + UUID.randomUUID();
        AgentCredentialView created = service.createAgent(name);
        byte[] storedDigest = jdbcTemplate.queryForObject(
                "SELECT api_key_digest FROM agents WHERE id = ?",
                byte[].class,
                created.id()
        );
        assertThat(storedDigest).hasSize(32);
        assertThat(apiKeyService.authenticate(created.apiKey())).isPresent();

        AgentCredentialView reset = service.resetApiKey(created.id());
        assertThat(apiKeyService.authenticate(created.apiKey())).isEmpty();
        assertThat(apiKeyService.authenticate(reset.apiKey())).isPresent();

        assertThatThrownBy(() -> service.createAgent(name))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }
}
