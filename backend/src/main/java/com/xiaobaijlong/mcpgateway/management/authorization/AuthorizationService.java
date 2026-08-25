package com.xiaobaijlong.mcpgateway.management.authorization;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@Service
public class AuthorizationService {

    private final AuthorizationRepository repository;
    private final AgentApiKeyService apiKeyService;

    public AuthorizationService(AuthorizationRepository repository, AgentApiKeyService apiKeyService) {
        this.repository = repository;
        this.apiKeyService = apiKeyService;
    }

    @Transactional
    public AgentCredentialView createAgent(String name) {
        AgentApiKeyService.KeyMaterial key = apiKeyService.generate();
        Instant now = Instant.now();
        try {
            long id = repository.createAgent(name, key.prefix(), key.digest(), now);
            return new AgentCredentialView(id, name, key.prefix(), key.plainText());
        } catch (DuplicateKeyException exception) {
            throw conflict("Agent 名称已存在", exception);
        }
    }

    public List<AgentView> getAgents() {
        return repository.findAgents().stream().map(this::toAgentView).toList();
    }

    public AgentView getAgent(long id) {
        return toAgentView(requireAgent(id));
    }

    @Transactional
    public AgentView updateAgent(long id, String name) {
        requireAgent(id);
        try {
            repository.updateAgent(id, name, Instant.now());
            return getAgent(id);
        } catch (DuplicateKeyException exception) {
            throw conflict("Agent 名称已存在", exception);
        }
    }

    @Transactional
    public void deleteAgent(long id) {
        if (repository.deleteAgent(id) == 0) {
            throw notFound("Agent 不存在");
        }
    }

    @Transactional
    public AgentCredentialView resetApiKey(long id) {
        AuthorizationRepository.AgentRow agent = requireAgent(id);
        AgentApiKeyService.KeyMaterial key = apiKeyService.generate();
        repository.resetApiKey(id, key.prefix(), key.digest(), Instant.now());
        return new AgentCredentialView(agent.id(), agent.name(), key.prefix(), key.plainText());
    }

    @Transactional
    public void addAgentRole(long agentId, long roleId) {
        requireAgent(agentId);
        requireRole(roleId);
        if (!repository.findRoleIdsForAgent(agentId).contains(roleId)) {
            repository.addAgentRole(agentId, roleId);
        }
    }

    @Transactional
    public void removeAgentRole(long agentId, long roleId) {
        requireAgent(agentId);
        requireRole(roleId);
        repository.removeAgentRole(agentId, roleId);
    }

    public List<String> getPermissionToolNames(long agentId) {
        requireAgent(agentId);
        return repository.findPermissionToolNames(agentId);
    }

    @Transactional
    public RoleView createRole(String name, String description) {
        try {
            return getRole(repository.createRole(name, description));
        } catch (DuplicateKeyException exception) {
            throw conflict("角色名称已存在", exception);
        }
    }

    public List<RoleView> getRoles() {
        return repository.findRoles().stream().map(this::toRoleView).toList();
    }

    public RoleView getRole(long id) {
        return toRoleView(requireRole(id));
    }

    @Transactional
    public RoleView updateRole(long id, String name, String description) {
        requireRole(id);
        try {
            repository.updateRole(id, name, description);
            return getRole(id);
        } catch (DuplicateKeyException exception) {
            throw conflict("角色名称已存在", exception);
        }
    }

    @Transactional
    public void deleteRole(long id) {
        if (repository.deleteRole(id) == 0) {
            throw notFound("角色不存在");
        }
    }

    @Transactional
    public void addRoleToolSet(long roleId, long toolSetId) {
        requireRole(roleId);
        requireToolSet(toolSetId);
        if (!repository.findToolSetIdsForRole(roleId).contains(toolSetId)) {
            repository.addRoleToolSet(roleId, toolSetId);
        }
    }

    @Transactional
    public void removeRoleToolSet(long roleId, long toolSetId) {
        requireRole(roleId);
        requireToolSet(toolSetId);
        repository.removeRoleToolSet(roleId, toolSetId);
    }

    @Transactional
    public ToolSetView createToolSet(String name, String description, List<String> toolNames) {
        try {
            long id = repository.createToolSet(name, description);
            repository.replaceToolSetMembers(id, normalizeToolNames(toolNames));
            return getToolSet(id);
        } catch (DuplicateKeyException exception) {
            throw conflict("工具集名称或成员重复", exception);
        }
    }

    public List<ToolSetView> getToolSets() {
        return repository.findToolSets().stream().map(this::toToolSetView).toList();
    }

    public ToolSetView getToolSet(long id) {
        return toToolSetView(requireToolSet(id));
    }

    @Transactional
    public ToolSetView updateToolSet(long id, String name, String description, List<String> toolNames) {
        requireToolSet(id);
        try {
            repository.updateToolSet(id, name, description);
            // 每次保存都替换为请求中的明确成员，绝不保留筛选条件或动态查询。
            repository.replaceToolSetMembers(id, normalizeToolNames(toolNames));
            return getToolSet(id);
        } catch (DuplicateKeyException exception) {
            throw conflict("工具集名称或成员重复", exception);
        }
    }

    @Transactional
    public void deleteToolSet(long id) {
        if (repository.deleteToolSet(id) == 0) {
            throw notFound("工具集不存在");
        }
    }

    private AgentView toAgentView(AuthorizationRepository.AgentRow row) {
        return new AgentView(
                row.id(),
                row.name(),
                row.apiKeyPrefix(),
                repository.findRoleIdsForAgent(row.id()),
                row.createdAt(),
                row.updatedAt()
        );
    }

    private RoleView toRoleView(AuthorizationRepository.RoleRow row) {
        return new RoleView(
                row.id(),
                row.name(),
                row.description(),
                repository.findToolSetIdsForRole(row.id())
        );
    }

    private ToolSetView toToolSetView(AuthorizationRepository.ToolSetRow row) {
        return new ToolSetView(
                row.id(),
                row.name(),
                row.description(),
                repository.findToolNamesForToolSet(row.id())
        );
    }

    private List<String> normalizeToolNames(List<String> toolNames) {
        return toolNames.stream().map(String::trim).distinct().sorted().toList();
    }

    private AuthorizationRepository.AgentRow requireAgent(long id) {
        return repository.findAgent(id).orElseThrow(() -> notFound("Agent 不存在"));
    }

    private AuthorizationRepository.RoleRow requireRole(long id) {
        return repository.findRole(id).orElseThrow(() -> notFound("角色不存在"));
    }

    private AuthorizationRepository.ToolSetRow requireToolSet(long id) {
        return repository.findToolSet(id).orElseThrow(() -> notFound("工具集不存在"));
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private ResponseStatusException conflict(String message, Exception cause) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message, cause);
    }
}
