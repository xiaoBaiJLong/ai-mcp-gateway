package com.lon.mcpgateway.gateway.infrastructure.agent;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
interface AgentMapper {
    @Insert("insert into agents (id, name, description, created_at) values (#{id}, #{name}, #{description}, #{createdAt})")
    void insertAgent(StoredAgent agent);

    @Insert("insert into agent_credentials (id, agent_id, key_hash, key_prefix, created_at, enabled) values (#{id}, #{agentId}, #{keyHash}, #{keyPrefix}, #{createdAt}, #{enabled})")
    void insertCredential(StoredCredential credential);

    @Select("select id, name, description, created_at as createdAt from agents where id = #{agentId}")
    AgentRow findAgent(String agentId);

    @Select("select id, name, description, created_at as createdAt from agents where id = #{agentId} for update")
    AgentRow findAgentForUpdate(String agentId);

    @Select("select id, name, description, created_at as createdAt from agents order by created_at")
    List<AgentRow> findAllAgents();

    @Select("select c.id, c.agent_id as agentId, c.key_prefix as prefix, c.created_at as createdAt, c.enabled from agent_credentials c join agents a on a.id = c.agent_id where c.agent_id = #{agentId} order by case when c.id = a.current_credential_id then 0 else 1 end, c.created_at desc, c.id desc")
    List<CredentialRow> findCredentials(String agentId);

    @Select("select c.id, c.agent_id as agentId, c.key_prefix as prefix, c.created_at as createdAt, c.enabled from agent_credentials c join agents a on a.current_credential_id = c.id where a.id = #{agentId}")
    CredentialRow findCurrentCredential(String agentId);

    @Update("update agent_credentials set enabled = false where agent_id = #{agentId} and enabled = true")
    void disableEnabledCredentials(String agentId);

    @Update("update agent_credentials set enabled = #{enabled} where id = #{credentialId}")
    void updateCredentialEnabled(String credentialId, boolean enabled);

    @Update("update agents set current_credential_id = #{credentialId} where id = #{agentId}")
    void updateCurrentCredential(String agentId, String credentialId);

    @Delete("delete from agent_tool_assignments where agent_id = #{agentId}")
    void deleteToolAssignments(String agentId);

    @Insert("insert into agent_tool_assignments (agent_id, tool_id) values (#{agentId}, #{toolId})")
    void insertToolAssignment(String agentId, String toolId);

    @Select("select t.id, t.name, t.description, t.enabled from agent_tool_assignments a join mcp_tools t on t.id = a.tool_id where a.agent_id = #{agentId} order by t.created_at")
    List<ToolRow> findToolSnapshot(String agentId);

    record StoredAgent(String id, String name, String description, Instant createdAt) {
    }

    record StoredCredential(String id, String agentId, String keyHash, String keyPrefix, Instant createdAt, boolean enabled) {
    }

    record AgentRow(String id, String name, String description, Instant createdAt) {
    }

    record CredentialRow(String id, String agentId, String prefix, Instant createdAt, boolean enabled) {
    }

    record ToolRow(String id, String name, String description, boolean enabled) {
    }
}
