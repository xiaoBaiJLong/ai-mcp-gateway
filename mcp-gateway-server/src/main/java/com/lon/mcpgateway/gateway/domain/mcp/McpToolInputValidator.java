package com.lon.mcpgateway.gateway.domain.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Iterator;
import java.util.Map;

final class McpToolInputValidator {
    String validate(JsonNode schema, JsonNode value) {
        return validate(schema, value, "arguments");
    }

    private String validate(JsonNode schema, JsonNode value, String path) {
        if (value == null || value.isMissingNode() || value.isNull()) {
            return schema.path("nullable").asBoolean() ? null : path + " 不能为空";
        }
        String type = schema.path("type").asText();
        if (!matchesType(type, value)) {
            return path + " 类型无效";
        }
        if (schema.has("enum") && !contains(schema.path("enum"), value)) {
            return path + " 不在允许值范围内";
        }
        if ("object".equals(type)) {
            JsonNode properties = schema.path("properties");
            Iterator<String> names = value.fieldNames();
            while (names.hasNext()) {
                String name = names.next();
                if (!properties.has(name)) {
                    return path + "." + name + " 不受支持";
                }
            }
            for (JsonNode required : schema.path("required")) {
                String name = required.asText();
                if (!value.has(name) || value.path(name).isNull()) {
                    return path + "." + name + " 是必填项";
                }
            }
            Iterator<Map.Entry<String, JsonNode>> fields = properties.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (value.has(field.getKey())) {
                    String error = validate(field.getValue(), value.path(field.getKey()), path + "." + field.getKey());
                    if (error != null) {
                        return error;
                    }
                }
            }
        }
        if ("array".equals(type)) {
            for (int index = 0; index < value.size(); index++) {
                String error = validate(schema.path("items"), value.get(index), path + "[" + index + "]");
                if (error != null) {
                    return error;
                }
            }
        }
        return null;
    }

    private boolean matchesType(String type, JsonNode value) {
        return switch (type) {
            case "object" -> value.isObject();
            case "array" -> value.isArray();
            case "string" -> value.isTextual();
            case "number" -> value.isNumber();
            case "integer" -> value.isIntegralNumber();
            case "boolean" -> value.isBoolean();
            default -> false;
        };
    }

    private boolean contains(JsonNode values, JsonNode value) {
        for (JsonNode candidate : values) {
            if (candidate.equals(value)) {
                return true;
            }
        }
        return false;
    }
}
