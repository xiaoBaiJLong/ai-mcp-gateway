create table if not exists mcp_tools (
    id varchar(36) primary key,
    name varchar(128) not null,
    name_hash char(64) not null unique,
    description varchar(2000) not null,
    enabled boolean not null,
    created_at timestamp not null
);

create table if not exists http_mappings (
    tool_id varchar(36) primary key,
    service_name varchar(255) not null,
    http_method varchar(10) not null,
    normalized_path varchar(1000) not null,
    source_hash char(64) not null unique,
    input_schema longtext not null,
    operation_snapshot longtext not null,
    constraint fk_http_mapping_tool foreign key (tool_id) references mcp_tools(id)
);

create table if not exists agents (
    id varchar(36) primary key,
    name varchar(128) not null,
    description varchar(2000) not null,
    current_credential_id varchar(36),
    created_at timestamp not null
);

create table if not exists agent_credentials (
    id varchar(36) primary key,
    agent_id varchar(36) not null,
    key_hash char(64) not null unique,
    key_prefix varchar(32) not null,
    created_at timestamp not null,
    enabled boolean not null,
    constraint fk_agent_credential_agent foreign key (agent_id) references agents(id)
);

create table if not exists agent_tool_assignments (
    agent_id varchar(36) not null,
    tool_id varchar(36) not null,
    primary key (agent_id, tool_id),
    constraint fk_agent_tool_assignment_agent foreign key (agent_id) references agents(id),
    constraint fk_agent_tool_assignment_tool foreign key (tool_id) references mcp_tools(id)
);
