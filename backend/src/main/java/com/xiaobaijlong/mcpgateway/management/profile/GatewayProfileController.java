package com.xiaobaijlong.mcpgateway.management.profile;

import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/management/gateway-profile")
public class GatewayProfileController {

    private final GatewayProfileRepository repository;

    public GatewayProfileController(GatewayProfileRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public GatewayProfile get() {
        return repository.get();
    }

    @PutMapping
    public GatewayProfile update(
            @Valid @RequestBody UpdateGatewayProfileRequest request,
            Authentication authentication
    ) {
        return repository.update(request.name().trim(), authentication.getName());
    }
}
