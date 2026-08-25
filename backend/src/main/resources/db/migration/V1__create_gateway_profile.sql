CREATE TABLE gateway_profile (
    id BIGINT NOT NULL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    updated_by VARCHAR(64) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);

INSERT INTO gateway_profile (id, name, updated_by)
VALUES (1, '本地 MCP 网关', 'system');
