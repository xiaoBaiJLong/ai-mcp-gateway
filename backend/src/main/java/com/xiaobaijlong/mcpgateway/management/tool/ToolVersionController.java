package com.xiaobaijlong.mcpgateway.management.tool;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/management/tool-versions")
public class ToolVersionController {

    private final ToolManagementService service;

    public ToolVersionController(ToolManagementService service) {
        this.service = service;
    }

    @GetMapping
    public List<ToolVersionView> list(@RequestParam(required = false) String toolName) {
        return service.getVersions(toolName);
    }

    @GetMapping("/{id}")
    public ToolVersionView get(@PathVariable long id) {
        return service.getVersion(id);
    }

    @PostMapping("/{id}/draft")
    public ResponseEntity<ToolDraftView> createDraft(
            @PathVariable long id,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                service.createDraftFromVersion(id, authentication.getName())
        );
    }
}
