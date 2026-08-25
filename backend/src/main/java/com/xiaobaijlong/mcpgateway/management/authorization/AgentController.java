package com.xiaobaijlong.mcpgateway.management.authorization;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/management/agents")
public class AgentController {

    private final AuthorizationService service;

    public AgentController(AuthorizationService service) {
        this.service = service;
    }

    @GetMapping
    public List<AgentView> list() {
        return service.getAgents();
    }

    @GetMapping("/{id}")
    public AgentView get(@PathVariable long id) {
        return service.getAgent(id);
    }

    @PostMapping
    public ResponseEntity<AgentCredentialView> create(@Valid @RequestBody AgentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.createAgent(request.name().trim()));
    }

    @PutMapping("/{id}")
    public AgentView update(@PathVariable long id, @Valid @RequestBody AgentRequest request) {
        return service.updateAgent(id, request.name().trim());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        service.deleteAgent(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/reset-api-key")
    public AgentCredentialView resetApiKey(@PathVariable long id) {
        return service.resetApiKey(id);
    }

    @PostMapping("/{agentId}/roles/{roleId}")
    public ResponseEntity<Void> addRole(@PathVariable long agentId, @PathVariable long roleId) {
        service.addAgentRole(agentId, roleId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{agentId}/roles/{roleId}")
    public ResponseEntity<Void> removeRole(@PathVariable long agentId, @PathVariable long roleId) {
        service.removeAgentRole(agentId, roleId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/permissions")
    public PermissionView permissions(@PathVariable long id) {
        return new PermissionView(service.getPermissionToolNames(id));
    }

    public record AgentRequest(
            @NotBlank(message = "Agent 名称不能为空")
            @Size(max = 100, message = "Agent 名称不能超过 100 个字符")
            String name
    ) {
    }

    public record PermissionView(List<String> toolNames) {
    }
}
