package com.lon.mcpgateway.mockuser.api;

import com.lon.mcpgateway.mockuser.config.MockUserProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping(path = "/api/users", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "用户模拟服务", description = "用于验证 MCP 网关 HTTP 工具映射的用户接口")
public class UserController {

    private final MockUserProperties mockUserProperties;

    public UserController(MockUserProperties mockUserProperties) {
        this.mockUserProperties = mockUserProperties;
    }

    @GetMapping("/{userId}")
    @Operation(summary = "按用户 ID 查询用户")
    public Mono<ResponseEntity<?>> getUser(
            @PathVariable String userId,
            @RequestParam(defaultValue = "false") boolean verbose) {
        if ("not-found".equals(userId)) {
            return Mono.just(ResponseEntity.status(404)
                    .body(new ErrorResponse("USER_NOT_FOUND", "未找到指定用户")));
        }
        if ("slow".equals(userId)) {
            return Mono.delay(mockUserProperties.getSlowResponseDelay())
                    .map(ignored -> ResponseEntity.ok(new UserResponse(userId, "演示用户", verbose)));
        }
        return Mono.just(ResponseEntity.ok(new UserResponse(userId, "演示用户", verbose)));
    }

    @PostMapping(path = "/search", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "按关键词搜索用户")
    public Mono<ResponseEntity<?>> searchUsers(@RequestBody UserSearchRequest request) {
        if ("server-error".equals(request.keyword())) {
            return Mono.just(ResponseEntity.status(500)
                    .body(new ErrorResponse("USER_SEARCH_FAILED", "用户搜索模拟失败")));
        }
        UserResponse user = new UserResponse("u-100", "演示用户", false);
        return Mono.just(ResponseEntity.ok(new UserSearchResponse(request.keyword(), request.page(), List.of(user))));
    }

    public record UserResponse(String id, String name, boolean verbose) {
    }

    public record UserSearchRequest(String keyword, Integer page) {
    }

    public record UserSearchResponse(String keyword, Integer page, List<UserResponse> items) {
    }

    public record ErrorResponse(String code, String message) {
    }
}
