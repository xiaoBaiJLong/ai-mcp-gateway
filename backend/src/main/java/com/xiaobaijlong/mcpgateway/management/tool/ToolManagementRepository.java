package com.xiaobaijlong.mcpgateway.management.tool;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class ToolManagementRepository {

    private final JdbcTemplate jdbcTemplate;
    private final SimpleJdbcInsert upstreamInsert;
    private final SimpleJdbcInsert draftInsert;
    private final SimpleJdbcInsert versionInsert;

    public ToolManagementRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.upstreamInsert = insert("static_http_upstreams");
        this.draftInsert = insert("tool_drafts");
        this.versionInsert = insert("tool_versions");
    }

    long createUpstream(
            String serviceId,
            String displayName,
            String baseUrl,
            ConnectivityStatus status,
            String error,
            String actor,
            Instant now
    ) {
        Map<String, Object> values = new HashMap<>();
        values.put("service_id", serviceId);
        values.put("display_name", displayName);
        values.put("base_url", baseUrl);
        values.put("connectivity_status", status.name());
        values.put("connectivity_error", error);
        values.put("last_checked_at", Timestamp.from(now));
        values.put("created_by", actor);
        values.put("created_at", Timestamp.from(now));
        values.put("updated_at", Timestamp.from(now));
        return upstreamInsert.executeAndReturnKey(values).longValue();
    }

    List<UpstreamRow> findUpstreams() {
        return jdbcTemplate.query("SELECT * FROM static_http_upstreams ORDER BY id", this::mapUpstream);
    }

    Optional<UpstreamRow> findUpstream(long id) {
        return jdbcTemplate.query(
                "SELECT * FROM static_http_upstreams WHERE id = ?", this::mapUpstream, id
        ).stream().findFirst();
    }

    void updateConnectivity(long id, ConnectivityStatus status, String error, Instant now) {
        jdbcTemplate.update("""
                UPDATE static_http_upstreams
                SET connectivity_status = ?, connectivity_error = ?, last_checked_at = ?, updated_at = ?
                WHERE id = ?
                """, status.name(), error, Timestamp.from(now), Timestamp.from(now), id);
    }

    long createDraft(DraftValues values, String actor, Instant now) {
        Map<String, Object> columns = draftColumns(values);
        columns.put("validation_status", ValidationStatus.UNVALIDATED.name());
        columns.put("validation_error", "");
        columns.put("created_by", actor);
        columns.put("created_at", Timestamp.from(now));
        columns.put("updated_at", Timestamp.from(now));
        return draftInsert.executeAndReturnKey(columns).longValue();
    }

    List<DraftRow> findDrafts() {
        return jdbcTemplate.query(draftSelect() + " ORDER BY draft.id", this::mapDraft);
    }

    Optional<DraftRow> findDraft(long id) {
        return jdbcTemplate.query(draftSelect() + " WHERE draft.id = ?", this::mapDraft, id)
                .stream().findFirst();
    }

    void updateDraft(long id, DraftValues values, Instant now) {
        jdbcTemplate.update("""
                UPDATE tool_drafts
                SET display_name = ?, risk_level = ?, upstream_id = ?, http_method = ?, path = ?,
                    request_config = ?, response_config = ?, validation_status = 'UNVALIDATED',
                    validation_error = '', updated_at = ?
                WHERE id = ?
                """,
                values.displayName(), values.riskLevel().name(), values.upstreamId(), values.httpMethod(), values.path(),
                values.requestConfig(), values.responseConfig(), Timestamp.from(now), id
        );
    }

    void updateValidation(long id, ValidationStatus status, List<String> errors, Instant now) {
        jdbcTemplate.update("""
                UPDATE tool_drafts SET validation_status = ?, validation_error = ?, updated_at = ? WHERE id = ?
                """, status.name(), String.join("|", errors), Timestamp.from(now), id);
    }

    void deleteDraft(long id) {
        jdbcTemplate.update("DELETE FROM tool_drafts WHERE id = ?", id);
    }

    boolean lockDraft(long id) {
        return !jdbcTemplate.queryForList(
                "SELECT id FROM tool_drafts WHERE id = ? FOR UPDATE", Long.class, id
        ).isEmpty();
    }

    void lockUpstream(long upstreamId) {
        // 同一工具名称固定绑定同一 serviceId，锁住上游即可串行化该上游下的版本号分配。
        jdbcTemplate.queryForObject(
                "SELECT id FROM static_http_upstreams WHERE id = ? FOR UPDATE", Long.class, upstreamId
        );
    }

    int nextVersionNumber(String toolName) {
        Integer current = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(version_number), 0) FROM tool_versions WHERE tool_name = ?",
                Integer.class,
                toolName
        );
        return (current == null ? 0 : current) + 1;
    }

    long createVersion(DraftRow draft, int versionNumber, String actor, Instant now) {
        Map<String, Object> columns = new HashMap<>();
        columns.put("tool_name", draft.toolName());
        columns.put("version_number", versionNumber);
        columns.put("display_name", draft.displayName());
        columns.put("risk_level", draft.riskLevel().name());
        columns.put("upstream_id", draft.upstreamId());
        columns.put("http_method", draft.httpMethod());
        columns.put("path", draft.path());
        columns.put("request_config", draft.requestConfig());
        columns.put("response_config", draft.responseConfig());
        columns.put("published_by", actor);
        columns.put("published_at", Timestamp.from(now));
        return versionInsert.executeAndReturnKey(columns).longValue();
    }

    List<VersionRow> findVersions(String toolName) {
        String sql = versionSelect();
        if (toolName == null || toolName.isBlank()) {
            return jdbcTemplate.query(sql + " ORDER BY version.tool_name, version.version_number", this::mapVersion);
        }
        return jdbcTemplate.query(
                sql + " WHERE version.tool_name = ? ORDER BY version.version_number", this::mapVersion, toolName
        );
    }

    Optional<VersionRow> findVersion(long id) {
        return jdbcTemplate.query(versionSelect() + " WHERE version.id = ?", this::mapVersion, id)
                .stream().findFirst();
    }

    int maxVersionNumber(String toolName) {
        Integer current = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(version_number), 0) FROM tool_versions WHERE tool_name = ?",
                Integer.class,
                toolName
        );
        return current == null ? 0 : current;
    }

    private SimpleJdbcInsert insert(String table) {
        return new SimpleJdbcInsert(jdbcTemplate).withTableName(table).usingGeneratedKeyColumns("id");
    }

    private Map<String, Object> draftColumns(DraftValues values) {
        Map<String, Object> columns = new HashMap<>();
        columns.put("tool_name", values.toolName());
        columns.put("display_name", values.displayName());
        columns.put("risk_level", values.riskLevel().name());
        columns.put("upstream_id", values.upstreamId());
        columns.put("http_method", values.httpMethod());
        columns.put("path", values.path());
        columns.put("request_config", values.requestConfig());
        columns.put("response_config", values.responseConfig());
        return columns;
    }

    private String draftSelect() {
        return """
                SELECT draft.*, upstream.service_id
                FROM tool_drafts draft
                JOIN static_http_upstreams upstream ON upstream.id = draft.upstream_id
                """;
    }

    private String versionSelect() {
        return """
                SELECT version.*, upstream.service_id
                FROM tool_versions version
                JOIN static_http_upstreams upstream ON upstream.id = version.upstream_id
                """;
    }

    private UpstreamRow mapUpstream(ResultSet resultSet, int rowNumber) throws SQLException {
        Timestamp checkedAt = resultSet.getTimestamp("last_checked_at");
        return new UpstreamRow(
                resultSet.getLong("id"),
                resultSet.getString("service_id"),
                resultSet.getString("display_name"),
                resultSet.getString("base_url"),
                ConnectivityStatus.valueOf(resultSet.getString("connectivity_status")),
                resultSet.getString("connectivity_error"),
                checkedAt == null ? null : checkedAt.toInstant(),
                resultSet.getString("created_by"),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant()
        );
    }

    private DraftRow mapDraft(ResultSet resultSet, int rowNumber) throws SQLException {
        return new DraftRow(
                resultSet.getLong("id"), resultSet.getString("tool_name"), resultSet.getString("display_name"),
                RiskLevel.valueOf(resultSet.getString("risk_level")), resultSet.getLong("upstream_id"),
                resultSet.getString("service_id"), resultSet.getString("http_method"), resultSet.getString("path"),
                resultSet.getString("request_config"), resultSet.getString("response_config"),
                ValidationStatus.valueOf(resultSet.getString("validation_status")),
                resultSet.getString("validation_error"), resultSet.getString("created_by"),
                resultSet.getTimestamp("created_at").toInstant(), resultSet.getTimestamp("updated_at").toInstant()
        );
    }

    private VersionRow mapVersion(ResultSet resultSet, int rowNumber) throws SQLException {
        return new VersionRow(
                resultSet.getLong("id"), resultSet.getString("tool_name"), resultSet.getInt("version_number"),
                resultSet.getString("display_name"), RiskLevel.valueOf(resultSet.getString("risk_level")),
                resultSet.getLong("upstream_id"), resultSet.getString("service_id"),
                resultSet.getString("http_method"), resultSet.getString("path"),
                resultSet.getString("request_config"), resultSet.getString("response_config"),
                resultSet.getString("published_by"), resultSet.getTimestamp("published_at").toInstant()
        );
    }

    record UpstreamRow(
            long id, String serviceId, String displayName, String baseUrl, ConnectivityStatus connectivityStatus,
            String connectivityError, Instant lastCheckedAt, String createdBy, Instant createdAt, Instant updatedAt
    ) { }

    record DraftValues(
            String toolName, String displayName, RiskLevel riskLevel, long upstreamId, String httpMethod, String path,
            String requestConfig, String responseConfig
    ) { }

    record DraftRow(
            long id, String toolName, String displayName, RiskLevel riskLevel, long upstreamId, String serviceId,
            String httpMethod, String path, String requestConfig, String responseConfig,
            ValidationStatus validationStatus, String validationError, String createdBy, Instant createdAt, Instant updatedAt
    ) { }

    record VersionRow(
            long id, String toolName, int versionNumber, String displayName, RiskLevel riskLevel, long upstreamId,
            String serviceId, String httpMethod, String path, String requestConfig, String responseConfig,
            String publishedBy, Instant publishedAt
    ) { }
}
