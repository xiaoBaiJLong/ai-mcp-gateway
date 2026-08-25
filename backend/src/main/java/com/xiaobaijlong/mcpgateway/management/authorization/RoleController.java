package com.xiaobaijlong.mcpgateway.management.authorization;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
@RequestMapping("/api/management/roles")
public class RoleController {

    private final AuthorizationService service;

    public RoleController(AuthorizationService service) {
        this.service = service;
    }

    @GetMapping
    public List<RoleView> list() {
        return service.getRoles();
    }

    @GetMapping("/{id}")
    public RoleView get(@PathVariable long id) {
        return service.getRole(id);
    }

    @PostMapping
    public ResponseEntity<RoleView> create(@Valid @RequestBody RoleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                service.createRole(request.name().trim(), request.description().trim())
        );
    }

    @PutMapping("/{id}")
    public RoleView update(@PathVariable long id, @Valid @RequestBody RoleRequest request) {
        return service.updateRole(id, request.name().trim(), request.description().trim());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        service.deleteRole(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{roleId}/tool-sets/{toolSetId}")
    public ResponseEntity<Void> addToolSet(@PathVariable long roleId, @PathVariable long toolSetId) {
        service.addRoleToolSet(roleId, toolSetId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{roleId}/tool-sets/{toolSetId}")
    public ResponseEntity<Void> removeToolSet(@PathVariable long roleId, @PathVariable long toolSetId) {
        service.removeRoleToolSet(roleId, toolSetId);
        return ResponseEntity.noContent().build();
    }

    public record RoleRequest(
            @NotBlank(message = "角色名称不能为空")
            @Size(max = 100, message = "角色名称不能超过 100 个字符")
            String name,
            @NotNull(message = "角色描述不能为空")
            @Size(max = 500, message = "角色描述不能超过 500 个字符")
            String description
    ) {
    }
}
