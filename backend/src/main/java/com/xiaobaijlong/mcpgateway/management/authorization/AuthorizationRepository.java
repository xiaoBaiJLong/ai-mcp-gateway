package com.xiaobaijlong.mcpgateway.management.authorization;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class AuthorizationRepository {

    private final JdbcTemplate jdbcTemplate;
    private final SimpleJdbcInsert agentInsert;
    private final SimpleJdbcInsert roleInsert;
    private final SimpleJdbcInsert toolSetInsert;

    public AuthorizationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.agentInsert = new SimpleJdbcInsert(jdbcTemplate)
                .withTableName("agents")
                .usingGeneratedKeyColumns("id");
        this.roleInsert = new SimpleJdbcInsert(jdbcTemplate)
                .withTableName("roles")
                .usingGeneratedKeyColumns("id");
        this.toolSetInsert = new SimpleJdbcInsert(jdbcTemplate)
                .withTableName("tool_sets")
                .usingGeneratedKeyColumns("id");
    }

    long createAgent(String name, String apiKeyPrefix, byte[] apiKeyDigest, Instant now) {
        Number id = agentInsert.executeAndReturnKey(Map.of(
                "name", name,
                "api_key_prefix", apiKeyPrefix,
                "api_key_digest", apiKeyDigest,
                "created_at", Timestamp.from(now),
                "updated_at", Timestamp.from(now)
        ));
        return id.longValue();
    }

    List<AgentRow> findAgents() {
        return jdbcTemplate.query("""
                SELECT id, name, api_key_prefix, api_key_digest, created_at, updated_at
                FROM agents ORDER BY id
                """, (resultSet, rowNum) -> mapAgent(resultSet));
    }

    Optional<AgentRow> findAgent(long id) {
        return jdbcTemplate.query("""
                SELECT id, name, api_key_prefix, api_key_digest, created_at, updated_at
                FROM agents WHERE id = ?
                """, (resultSet, rowNum) -> mapAgent(resultSet), id).stream().findFirst();
    }

    List<AgentRow> findAgentsByApiKeyPrefix(String prefix) {
        return jdbcTemplate.query("""
                SELECT id, name, api_key_prefix, api_key_digest, created_at, updated_at
                FROM agents WHERE api_key_prefix = ? ORDER BY id
                """, (resultSet, rowNum) -> mapAgent(resultSet), prefix);
    }

    int updateAgent(long id, String name, Instant now) {
        return jdbcTemplate.update(
                "UPDATE agents SET name = ?, updated_at = ? WHERE id = ?",
                name, Timestamp.from(now), id
        );
    }

    int resetApiKey(long id, String prefix, byte[] digest, Instant now) {
        return jdbcTemplate.update("""
                UPDATE agents SET api_key_prefix = ?, api_key_digest = ?, updated_at = ? WHERE id = ?
                """, prefix, digest, Timestamp.from(now), id);
    }

    int deleteAgent(long id) {
        return jdbcTemplate.update("DELETE FROM agents WHERE id = ?", id);
    }

    List<Long> findRoleIdsForAgent(long agentId) {
        return jdbcTemplate.queryForList(
                "SELECT role_id FROM agent_roles WHERE agent_id = ? ORDER BY role_id",
                Long.class,
                agentId
        );
    }

    int addAgentRole(long agentId, long roleId) {
        return jdbcTemplate.update(
                "INSERT INTO agent_roles (agent_id, role_id) VALUES (?, ?)", agentId, roleId
        );
    }

    int removeAgentRole(long agentId, long roleId) {
        return jdbcTemplate.update(
                "DELETE FROM agent_roles WHERE agent_id = ? AND role_id = ?", agentId, roleId
        );
    }

    List<String> findPermissionToolNames(long agentId) {
        return jdbcTemplate.queryForList("""
                SELECT DISTINCT member.tool_name
                FROM agent_roles agent_role
                JOIN role_tool_sets role_tool_set ON role_tool_set.role_id = agent_role.role_id
                JOIN tool_set_members member ON member.tool_set_id = role_tool_set.tool_set_id
                WHERE agent_role.agent_id = ?
                ORDER BY member.tool_name
                """, String.class, agentId);
    }

    long createRole(String name, String description) {
        return roleInsert.executeAndReturnKey(Map.of("name", name, "description", description)).longValue();
    }

    List<RoleRow> findRoles() {
        return jdbcTemplate.query(
                "SELECT id, name, description FROM roles ORDER BY id",
                (resultSet, rowNum) -> new RoleRow(
                        resultSet.getLong("id"),
                        resultSet.getString("name"),
                        resultSet.getString("description")
                )
        );
    }

    Optional<RoleRow> findRole(long id) {
        return jdbcTemplate.query(
                "SELECT id, name, description FROM roles WHERE id = ?",
                (resultSet, rowNum) -> new RoleRow(
                        resultSet.getLong("id"),
                        resultSet.getString("name"),
                        resultSet.getString("description")
                ),
                id
        ).stream().findFirst();
    }

    int updateRole(long id, String name, String description) {
        return jdbcTemplate.update(
                "UPDATE roles SET name = ?, description = ? WHERE id = ?", name, description, id
        );
    }

    int deleteRole(long id) {
        return jdbcTemplate.update("DELETE FROM roles WHERE id = ?", id);
    }

    List<Long> findToolSetIdsForRole(long roleId) {
        return jdbcTemplate.queryForList(
                "SELECT tool_set_id FROM role_tool_sets WHERE role_id = ? ORDER BY tool_set_id",
                Long.class,
                roleId
        );
    }

    int addRoleToolSet(long roleId, long toolSetId) {
        return jdbcTemplate.update(
                "INSERT INTO role_tool_sets (role_id, tool_set_id) VALUES (?, ?)", roleId, toolSetId
        );
    }

    int removeRoleToolSet(long roleId, long toolSetId) {
        return jdbcTemplate.update(
                "DELETE FROM role_tool_sets WHERE role_id = ? AND tool_set_id = ?", roleId, toolSetId
        );
    }

    long createToolSet(String name, String description) {
        return toolSetInsert.executeAndReturnKey(Map.of("name", name, "description", description)).longValue();
    }

    List<ToolSetRow> findToolSets() {
        return jdbcTemplate.query(
                "SELECT id, name, description FROM tool_sets ORDER BY id",
                (resultSet, rowNum) -> new ToolSetRow(
                        resultSet.getLong("id"),
                        resultSet.getString("name"),
                        resultSet.getString("description")
                )
        );
    }

    Optional<ToolSetRow> findToolSet(long id) {
        return jdbcTemplate.query(
                "SELECT id, name, description FROM tool_sets WHERE id = ?",
                (resultSet, rowNum) -> new ToolSetRow(
                        resultSet.getLong("id"),
                        resultSet.getString("name"),
                        resultSet.getString("description")
                ),
                id
        ).stream().findFirst();
    }

    int updateToolSet(long id, String name, String description) {
        return jdbcTemplate.update(
                "UPDATE tool_sets SET name = ?, description = ? WHERE id = ?", name, description, id
        );
    }

    int deleteToolSet(long id) {
        return jdbcTemplate.update("DELETE FROM tool_sets WHERE id = ?", id);
    }

    List<String> findToolNamesForToolSet(long toolSetId) {
        return jdbcTemplate.queryForList(
                "SELECT tool_name FROM tool_set_members WHERE tool_set_id = ? ORDER BY tool_name",
                String.class,
                toolSetId
        );
    }

    void replaceToolSetMembers(long toolSetId, List<String> toolNames) {
        jdbcTemplate.update("DELETE FROM tool_set_members WHERE tool_set_id = ?", toolSetId);
        List<Object[]> arguments = new ArrayList<>();
        for (String toolName : toolNames) {
            arguments.add(new Object[]{toolSetId, toolName});
        }
        if (!arguments.isEmpty()) {
            jdbcTemplate.batchUpdate(
                    "INSERT INTO tool_set_members (tool_set_id, tool_name) VALUES (?, ?)",
                    arguments
            );
        }
    }

    private AgentRow mapAgent(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        return new AgentRow(
                resultSet.getLong("id"),
                resultSet.getString("name"),
                resultSet.getString("api_key_prefix"),
                resultSet.getBytes("api_key_digest"),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant()
        );
    }

    record AgentRow(
            long id,
            String name,
            String apiKeyPrefix,
            byte[] apiKeyDigest,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    record RoleRow(long id, String name, String description) {
    }

    record ToolSetRow(long id, String name, String description) {
    }
}
