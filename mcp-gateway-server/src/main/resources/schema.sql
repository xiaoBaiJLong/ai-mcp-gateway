create table if not exists mcp_tools (
    id varchar(36) primary key comment 'MCP 工具唯一标识',
    name varchar(128) not null comment '工具名称，需满足 MCP 名称规范且全局唯一',
    name_hash char(64) not null unique comment '工具名称的 SHA-256 哈希，用于唯一性校验',
    description varchar(2000) not null comment '工具说明',
    enabled boolean not null comment '是否启用：true 启用，false 停用',
    created_at timestamp not null comment '创建时间'
) comment = 'MCP 工具表';

create table if not exists http_mappings (
    tool_id varchar(36) primary key comment '关联的 MCP 工具标识，由应用层维护关联关系',
    service_name varchar(255) not null comment 'Nacos 业务服务名称',
    http_method varchar(10) not null comment 'HTTP 请求方法',
    normalized_path varchar(1000) not null comment '规范化后的接口路径',
    source_hash char(64) not null unique comment '服务名称、请求方法和路径的 SHA-256 哈希，用于来源唯一性校验',
    input_schema longtext not null comment '由 OpenAPI 生成的工具输入 JSON Schema',
    operation_snapshot longtext not null comment '导入时保存的 OpenAPI Operation 快照'
) comment = 'MCP 工具 HTTP 映射表';

create table if not exists agents (
    id varchar(36) primary key comment '智能体唯一标识',
    name varchar(128) not null comment '智能体名称',
    description varchar(2000) not null comment '智能体说明',
    current_credential_id varchar(36) comment '当前启用的凭证标识，由应用层维护关联关系',
    created_at timestamp not null comment '创建时间'
) comment = '智能体表';

create table if not exists agent_credentials (
    id varchar(36) primary key comment '智能体凭证唯一标识',
    agent_id varchar(36) not null comment '所属智能体标识，由应用层维护关联关系',
    key_hash char(64) not null unique comment 'Agent Key 的 SHA-256 哈希',
    key_prefix varchar(32) not null comment 'Agent Key 可识别前缀，不包含完整密钥',
    created_at timestamp not null comment '创建时间',
    enabled boolean not null comment '是否启用：true 启用，false 禁用'
) comment = '智能体访问凭证表';

create table if not exists agent_tool_assignments (
    agent_id varchar(36) not null comment '智能体标识，由应用层维护关联关系',
    tool_id varchar(36) not null comment 'MCP 工具标识，由应用层维护关联关系',
    primary key (agent_id, tool_id)
) comment = '智能体工具快照分配表';
