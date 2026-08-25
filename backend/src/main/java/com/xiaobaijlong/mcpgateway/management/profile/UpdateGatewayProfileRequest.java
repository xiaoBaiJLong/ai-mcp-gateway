package com.xiaobaijlong.mcpgateway.management.profile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateGatewayProfileRequest(
        @NotBlank(message = "网关名称不能为空")
        @Size(max = 100, message = "网关名称不能超过 100 个字符")
        String name
) {
}
