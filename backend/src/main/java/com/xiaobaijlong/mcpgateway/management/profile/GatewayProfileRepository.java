package com.xiaobaijlong.mcpgateway.management.profile;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;

@Repository
public class GatewayProfileRepository {

    private static final long SINGLE_PROFILE_ID = 1L;

    private final JdbcTemplate jdbcTemplate;

    public GatewayProfileRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public GatewayProfile get() {
        return jdbcTemplate.queryForObject(
                "SELECT name, updated_by, updated_at FROM gateway_profile WHERE id = ?",
                (resultSet, rowNum) -> new GatewayProfile(
                        resultSet.getString("name"),
                        resultSet.getString("updated_by"),
                        resultSet.getTimestamp("updated_at").toInstant()
                ),
                SINGLE_PROFILE_ID
        );
    }

    public GatewayProfile update(String name, String updatedBy) {
        Instant updatedAt = Instant.now();
        jdbcTemplate.update(
                "UPDATE gateway_profile SET name = ?, updated_by = ?, updated_at = ? WHERE id = ?",
                name,
                updatedBy,
                Timestamp.from(updatedAt),
                SINGLE_PROFILE_ID
        );
        return get();
    }
}
