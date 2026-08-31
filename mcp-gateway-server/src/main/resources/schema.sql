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
    input_schema clob not null,
    operation_snapshot clob not null,
    constraint fk_http_mapping_tool foreign key (tool_id) references mcp_tools(id)
);
