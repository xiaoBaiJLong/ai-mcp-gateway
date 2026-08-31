package com.lon.mcpgateway.gateway.tool;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
interface ToolMapper {
    @Insert("insert into mcp_tools (id, name, name_hash, description, enabled, created_at) values (#{id}, #{name}, #{nameHash}, #{description}, #{enabled}, #{createdAt})")
    void insertTool(StoredTool tool);

    @Insert("insert into http_mappings (tool_id, service_name, http_method, normalized_path, source_hash, input_schema, operation_snapshot) values (#{toolId}, #{serviceName}, #{method}, #{path}, #{sourceHash}, #{inputSchema}, #{operationSnapshot})")
    void insertMapping(StoredMapping mapping);

    @Select("select count(*) from mcp_tools where name_hash = #{nameHash}")
    int countByNameHash(String nameHash);

    @Select("select count(*) from http_mappings where source_hash = #{sourceHash}")
    int countBySourceHash(String sourceHash);

    @Select("select t.id, t.name, t.description, t.enabled, t.created_at as createdAt, m.service_name as serviceName, m.http_method as method, m.normalized_path as path, m.input_schema as inputSchema from mcp_tools t join http_mappings m on m.tool_id = t.id order by t.created_at")
    List<ToolRow> findAll();

    record StoredTool(String id, String name, String nameHash, String description, boolean enabled, Instant createdAt) {
    }

    record StoredMapping(String toolId, String serviceName, String method, String path, String sourceHash,
            String inputSchema, String operationSnapshot) {
    }

    record ToolRow(String id, String name, String description, boolean enabled, Instant createdAt,
            String serviceName, String method, String path, String inputSchema) {
    }
}
