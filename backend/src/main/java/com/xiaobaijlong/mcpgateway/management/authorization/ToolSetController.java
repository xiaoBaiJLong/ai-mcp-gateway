package com.xiaobaijlong.mcpgateway.management.authorization;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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
@RequestMapping("/api/management/tool-sets")
public class ToolSetController {

    private final AuthorizationService service;

    public ToolSetController(AuthorizationService service) {
        this.service = service;
    }

    @GetMapping
    public List<ToolSetView> list() {
        return service.getToolSets();
    }

    @GetMapping("/{id}")
    public ToolSetView get(@PathVariable long id) {
        return service.getToolSet(id);
    }

    @PostMapping
    public ResponseEntity<ToolSetView> create(@Valid @RequestBody ToolSetRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                service.createToolSet(
                        request.name().trim(),
                        request.description().trim(),
                        request.toolNames()
                )
        );
    }

    @PutMapping("/{id}")
    public ToolSetView update(@PathVariable long id, @Valid @RequestBody ToolSetRequest request) {
        return service.updateToolSet(
                id,
                request.name().trim(),
                request.description().trim(),
                request.toolNames()
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        service.deleteToolSet(id);
        return ResponseEntity.noContent().build();
    }

    public record ToolSetRequest(
            @NotBlank(message = "工具集名称不能为空")
            @Size(max = 100, message = "工具集名称不能超过 100 个字符")
            String name,
            @NotNull(message = "工具集描述不能为空")
            @Size(max = 500, message = "工具集描述不能超过 500 个字符")
            String description,
            @NotNull(message = "工具成员不能为空")
            List<
                    @NotBlank(message = "工具名称不能为空")
                    @Size(max = 191, message = "工具名称不能超过 191 个字符")
                    @Pattern(
                            regexp = "[A-Za-z0-9_-]+\\.[A-Za-z0-9_.-]+",
                            message = "工具名称必须使用 <服务标识>.<工具标识> 格式"
                    ) String> toolNames
    ) {
    }
}
