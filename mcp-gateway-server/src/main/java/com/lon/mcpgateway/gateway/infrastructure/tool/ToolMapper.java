package com.lon.mcpgateway.gateway.infrastructure.tool;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
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

    @Select({"<script>",
            "select id, name, description, enabled from mcp_tools where enabled = true and id in",
            "<foreach item='toolId' collection='toolIds' open='(' separator=',' close=')'>#{toolId}</foreach>",
            "</script>"})
    List<EnabledToolRow> findEnabledToolsByIds(@Param("toolIds") List<String> toolIds);

    @Select("select t.id, t.name, t.description, m.input_schema as inputSchema, m.service_name as serviceName, m.http_method as method, m.normalized_path as path from agent_tool_assignments a join mcp_tools t on t.id = a.tool_id join http_mappings m on m.tool_id = t.id where a.agent_id = #{agentId} and t.enabled = true order by t.created_at")
    List<RuntimeToolRow> findEnabledToolsForAgent(String agentId);

    @Select("select t.id, t.name, t.description, m.input_schema as inputSchema, m.service_name as serviceName, m.http_method as method, m.normalized_path as path from agent_tool_assignments a join mcp_tools t on t.id = a.tool_id join http_mappings m on m.tool_id = t.id where a.agent_id = #{agentId} and t.enabled = true and t.name = #{toolName}")
    RuntimeToolRow findEnabledToolForAgent(String agentId, String toolName);

    record StoredTool(String id, String name, String nameHash, String description, boolean enabled, Instant createdAt) {
    }

    record StoredMapping(String toolId, String serviceName, String method, String path, String sourceHash,
            String inputSchema, String operationSnapshot) {
    }

    record ToolRow(String id, String name, String description, boolean enabled, Instant createdAt,
            String serviceName, String method, String path, String inputSchema) {
    }

    record EnabledToolRow(String id, String name, String description, boolean enabled) {
    }

    record RuntimeToolRow(String id, String name, String description, String inputSchema, String serviceName, String method,
            String path) {
    }
}
