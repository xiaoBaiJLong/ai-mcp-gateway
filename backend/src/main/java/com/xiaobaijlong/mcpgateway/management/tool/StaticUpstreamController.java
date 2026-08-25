package com.xiaobaijlong.mcpgateway.management.tool;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/management/upstreams")
public class StaticUpstreamController {

    private final ToolManagementService service;

    public StaticUpstreamController(ToolManagementService service) {
        this.service = service;
    }

    @GetMapping
    public List<StaticUpstreamView> list() {
        return service.getUpstreams();
    }

    @GetMapping("/{id}")
    public StaticUpstreamView get(@PathVariable long id) {
        return service.getUpstream(id);
    }

    @PostMapping
    public ResponseEntity<StaticUpstreamView> create(
            @Valid @RequestBody CreateUpstreamRequest request,
            Authentication authentication
    ) {
        StaticUpstreamView upstream = service.createUpstream(
                request.serviceId(), request.displayName(), request.baseUrl(), authentication.getName()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(upstream);
    }

    @PostMapping("/{id}/check")
    public StaticUpstreamView check(@PathVariable long id) {
        return service.checkUpstream(id);
    }

    public record CreateUpstreamRequest(
            @NotBlank(message = "服务标识不能为空")
            @Size(max = 63, message = "服务标识不能超过 63 个字符")
            String serviceId,
            @NotBlank(message = "显示名称不能为空")
            @Size(max = 200, message = "显示名称不能超过 200 个字符")
            String displayName,
            @NotBlank(message = "基础地址不能为空")
            @Size(max = 2048, message = "基础地址不能超过 2048 个字符")
            String baseUrl
    ) { }
}
