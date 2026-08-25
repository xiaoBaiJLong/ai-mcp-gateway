package com.xiaobaijlong.mcpgateway.management.tool;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/management/tool-drafts")
public class ToolDraftController {

    private final ToolManagementService service;

    public ToolDraftController(ToolManagementService service) {
        this.service = service;
    }

    @GetMapping
    public List<ToolDraftView> list() {
        return service.getDrafts();
    }

    @GetMapping("/{id}")
    public ToolDraftView get(@PathVariable long id) {
        return service.getDraft(id);
    }

    @PostMapping
    public ResponseEntity<ToolDraftView> create(
            @Valid @RequestBody ToolDraftRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                service.createDraft(request.toValues(), authentication.getName())
        );
    }

    @PutMapping("/{id}")
    public ToolDraftView update(@PathVariable long id, @Valid @RequestBody ToolDraftRequest request) {
        return service.updateDraft(id, request.toValues());
    }

    @PostMapping("/{id}/validate")
    public ToolDraftView validate(@PathVariable long id) {
        return service.validateDraft(id);
    }

    @PostMapping("/{id}/publish")
    public ResponseEntity<ToolVersionView> publish(@PathVariable long id, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.publish(id, authentication.getName()));
    }

    public record ToolDraftRequest(
            @NotBlank(message = "工具稳定名称不能为空")
            @Size(max = 191, message = "工具稳定名称不能超过 191 个字符")
            String toolName,
            @NotBlank(message = "显示名称不能为空")
            @Size(max = 200, message = "显示名称不能超过 200 个字符")
            String displayName,
            @NotNull(message = "风险等级不能为空")
            RiskLevel riskLevel,
            @Positive(message = "必须选择已登记上游")
            long upstreamId,
            @Size(max = 10, message = "HTTP 方法不能超过 10 个字符")
            String httpMethod,
            @Size(max = 500, message = "目标路径不能超过 500 个字符")
            String path,
            @Size(max = 4000, message = "请求配置不能超过 4000 个字符")
            String requestConfig,
            @Size(max = 4000, message = "响应配置不能超过 4000 个字符")
            String responseConfig
    ) {
        ToolManagementRepository.DraftValues toValues() {
            return new ToolManagementRepository.DraftValues(
                    toolName, displayName, riskLevel, upstreamId, httpMethod, path, requestConfig, responseConfig
            );
        }
    }
}
