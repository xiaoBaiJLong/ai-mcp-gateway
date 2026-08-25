CREATE TABLE agents (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    api_key_prefix VARCHAR(16) NOT NULL,
    api_key_digest BINARY(32) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_agents_name UNIQUE (name),
    CONSTRAINT uk_agents_api_key_digest UNIQUE (api_key_digest)
);

CREATE INDEX idx_agents_api_key_prefix ON agents (api_key_prefix);

CREATE TABLE roles (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500) NOT NULL DEFAULT '',
    CONSTRAINT uk_roles_name UNIQUE (name)
);

CREATE TABLE tool_sets (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500) NOT NULL DEFAULT '',
    CONSTRAINT uk_tool_sets_name UNIQUE (name)
);

CREATE TABLE tool_set_members (
    tool_set_id BIGINT NOT NULL,
    tool_name VARCHAR(191) NOT NULL,
    PRIMARY KEY (tool_set_id, tool_name),
    CONSTRAINT fk_tool_set_members_tool_set
        FOREIGN KEY (tool_set_id) REFERENCES tool_sets (id) ON DELETE CASCADE
);

CREATE TABLE role_tool_sets (
    role_id BIGINT NOT NULL,
    tool_set_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, tool_set_id),
    CONSTRAINT fk_role_tool_sets_role
        FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE,
    CONSTRAINT fk_role_tool_sets_tool_set
        FOREIGN KEY (tool_set_id) REFERENCES tool_sets (id) ON DELETE CASCADE
);

CREATE TABLE agent_roles (
    agent_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (agent_id, role_id),
    CONSTRAINT fk_agent_roles_agent
        FOREIGN KEY (agent_id) REFERENCES agents (id) ON DELETE CASCADE,
    CONSTRAINT fk_agent_roles_role
        FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE
);
