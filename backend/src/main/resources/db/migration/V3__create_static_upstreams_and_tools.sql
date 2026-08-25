CREATE TABLE static_http_upstreams (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    service_id VARCHAR(100) NOT NULL,
    display_name VARCHAR(200) NOT NULL,
    base_url VARCHAR(2048) NOT NULL,
    connectivity_status VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',
    connectivity_error VARCHAR(1000) NOT NULL DEFAULT '',
    last_checked_at TIMESTAMP(6) NULL,
    created_by VARCHAR(64) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_static_http_upstreams_service_id UNIQUE (service_id)
);

CREATE TABLE tool_drafts (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tool_name VARCHAR(191) NOT NULL,
    display_name VARCHAR(200) NOT NULL,
    risk_level VARCHAR(16) NOT NULL,
    upstream_id BIGINT NOT NULL,
    http_method VARCHAR(10) NOT NULL,
    path VARCHAR(500) NOT NULL,
    request_config VARCHAR(4000) NOT NULL DEFAULT '',
    response_config VARCHAR(4000) NOT NULL DEFAULT '',
    validation_status VARCHAR(20) NOT NULL DEFAULT 'UNVALIDATED',
    validation_error VARCHAR(1000) NOT NULL DEFAULT '',
    created_by VARCHAR(64) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_tool_drafts_name UNIQUE (tool_name),
    CONSTRAINT fk_tool_drafts_upstream
        FOREIGN KEY (upstream_id) REFERENCES static_http_upstreams (id)
);

CREATE TABLE tool_versions (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tool_name VARCHAR(191) NOT NULL,
    version_number INT NOT NULL,
    display_name VARCHAR(200) NOT NULL,
    risk_level VARCHAR(16) NOT NULL,
    upstream_id BIGINT NOT NULL,
    http_method VARCHAR(10) NOT NULL,
    path VARCHAR(500) NOT NULL,
    request_config VARCHAR(4000) NOT NULL DEFAULT '',
    response_config VARCHAR(4000) NOT NULL DEFAULT '',
    published_by VARCHAR(64) NOT NULL,
    published_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_tool_versions_name_version UNIQUE (tool_name, version_number),
    CONSTRAINT fk_tool_versions_upstream
        FOREIGN KEY (upstream_id) REFERENCES static_http_upstreams (id)
);
