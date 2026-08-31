package com.lon.mcpgateway.gateway.domain.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lon.mcpgateway.gateway.types.common.GatewayException;
import com.lon.mcpgateway.gateway.types.common.OpenApiImportException;
import com.lon.mcpgateway.gateway.types.tool.ToolModels.OperationView;
import com.lon.mcpgateway.gateway.types.tool.ToolModels.ToolDraftView;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
public final class OpenApiImporter {
    private static final Set<String> HTTP_METHODS = Set.of("GET", "PUT", "POST", "DELETE", "OPTIONS", "HEAD", "PATCH", "TRACE");

    public List<OperationView> operations(String serviceName, JsonNode document) {
        validateDocument(document);
        List<OperationView> operations = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> paths = document.path("paths").fields();
        while (paths.hasNext()) {
            Map.Entry<String, JsonNode> pathEntry = paths.next();
            JsonNode resolvedPathItem = resolveReference(document, pathEntry.getValue(), new HashSet<>());
            Iterator<Map.Entry<String, JsonNode>> pathItem = resolvedPathItem.fields();
            while (pathItem.hasNext()) {
                Map.Entry<String, JsonNode> candidate = pathItem.next();
                String method = candidate.getKey().toUpperCase(Locale.ROOT);
                if (!HTTP_METHODS.contains(method)) {
                    continue;
                }
                JsonNode operation = candidate.getValue();
                String unsupportedReason = unsupportedReason(document, resolvedPathItem, operation);
                operations.add(new OperationView(serviceName, method, normalizePath(pathEntry.getKey()), operation.path("operationId").asText(null),
                        operation.path("summary").asText(""), operation.path("description").asText(""), operation.path("deprecated").asBoolean(),
                        unsupportedReason == null, unsupportedReason));
            }
        }
        return operations;
    }

    public ToolDraftView draft(String serviceName, String method, String path, JsonNode document) {
        validateDocument(document);
        JsonNode pathItem = resolveReference(document, document.path("paths").path(normalizePath(path)), new HashSet<>());
        JsonNode operation = pathItem.path(method.toLowerCase(Locale.ROOT));
        if (operation.isMissingNode() || !operation.isObject()) {
            throw new GatewayException("OPERATION_NOT_FOUND", "未找到指定 OpenAPI operation");
        }
        String unsupportedReason = unsupportedReason(document, pathItem, operation);
        if (unsupportedReason != null) {
            throw new GatewayException("OPERATION_UNSUPPORTED", unsupportedReason);
        }
        String operationId = operation.path("operationId").asText("");
        String initialName = operationId.isBlank() ? "" : serviceName + "." + operationId;
        String description = operation.path("summary").asText("");
        String longDescription = operation.path("description").asText("");
        if (!longDescription.isBlank()) {
            description = description.isBlank() ? longDescription : description + "\n" + longDescription;
        }
        return new ToolDraftView(serviceName, method.toUpperCase(Locale.ROOT), normalizePath(path), initialName, description,
                inputSchema(document, pathItem, operation));
    }

    public JsonNode operation(String method, String path, JsonNode document) {
        JsonNode pathItem = resolveReference(document, document.path("paths").path(normalizePath(path)), new HashSet<>());
        return pathItem.path(method.toLowerCase(Locale.ROOT));
    }

    private String unsupportedReason(JsonNode document, JsonNode pathItem, JsonNode operation) {
        try {
            ensureLocalReferences(operation);
            ensureSupportedParameters(document, pathItem, operation);
            ensureSupportedRequestAndResponse(document, operation);
            inputSchema(document, pathItem, operation);
            return null;
        } catch (UnsupportedOperationException exception) {
            return exception.getMessage();
        }
    }

    private void validateDocument(JsonNode document) {
        if (!document.isObject() || !document.path("openapi").asText().startsWith("3.")) {
            throw new OpenApiImportException("文档不是 OpenAPI 3 JSON");
        }
        if (!document.path("paths").isObject()) {
            throw new OpenApiImportException("OpenAPI 文档缺少 paths");
        }
    }

    private void ensureLocalReferences(JsonNode node) {
        for (JsonNode reference : node.findValues("$ref")) {
            if (!reference.asText().startsWith("#/")) {
                throw new UnsupportedOperationException("当前版本不支持外部 OpenAPI 引用");
            }
        }
    }

    private void ensureSupportedParameters(JsonNode document, JsonNode pathItem, JsonNode operation) {
        for (JsonNode parameter : allParameters(document, pathItem, operation)) {
            String location = parameter.path("in").asText();
            if (!(location.equals("path") || location.equals("query") || location.equals("header"))) {
                throw new UnsupportedOperationException("当前版本不支持该参数位置");
            }
            String type = parameter.path("schema").path("type").asText();
            if (type.equals("array") || type.equals("object")) {
                throw new UnsupportedOperationException("当前版本不支持数组或对象参数序列化");
            }
            String style = parameter.path("style").asText("");
            String expectedStyle = location.equals("query") ? "form" : "simple";
            if (!style.isBlank() && !style.equals(expectedStyle)) {
                throw new UnsupportedOperationException("当前版本不支持该参数序列化方式");
            }
        }
    }

    private void ensureSupportedRequestAndResponse(JsonNode document, JsonNode operation) {
        JsonNode requestContent = resolveReference(document, operation.path("requestBody"), new HashSet<>()).path("content");
        if (requestContent.isObject() && !requestContent.isEmpty() && !requestContent.has("application/json")) {
            throw new UnsupportedOperationException("当前版本仅支持 JSON 请求体");
        }
        for (JsonNode response : operation.path("responses")) {
            response = resolveReference(document, response, new HashSet<>());
            JsonNode content = response.path("content");
            if (content.isObject() && !content.isEmpty() && !content.has("application/json")) {
                throw new UnsupportedOperationException("当前版本仅支持 JSON 响应");
            }
        }
    }

    private ObjectNode inputSchema(JsonNode document, JsonNode pathItem, JsonNode operation) {
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        root.put("type", "object");
        ObjectNode properties = root.putObject("properties");
        ArrayNode rootRequired = root.putArray("required");
        addParameterGroups(document, pathItem, operation, properties, rootRequired);
        JsonNode body = resolveReference(document, operation.path("requestBody"), new HashSet<>());
        JsonNode bodySchema = body.path("content").path("application/json").path("schema");
        if (!bodySchema.isMissingNode()) {
            properties.set("body", copySchema(document, bodySchema, new HashSet<>()));
            if (body.path("required").asBoolean()) {
                rootRequired.add("body");
            }
        }
        if (rootRequired.isEmpty()) {
            root.remove("required");
        }
        return root;
    }

    private void addParameterGroups(JsonNode document, JsonNode pathItem, JsonNode operation, ObjectNode properties, ArrayNode rootRequired) {
        Map<String, ObjectNode> groups = Map.of("path", JsonNodeFactory.instance.objectNode(), "query", JsonNodeFactory.instance.objectNode(),
                "header", JsonNodeFactory.instance.objectNode());
        Map<String, ArrayNode> required = Map.of("path", JsonNodeFactory.instance.arrayNode(), "query", JsonNodeFactory.instance.arrayNode(),
                "header", JsonNodeFactory.instance.arrayNode());
        for (JsonNode parameter : allParameters(document, pathItem, operation)) {
            String location = parameter.path("in").asText();
            ObjectNode group = groups.get(location);
            if (group == null) {
                continue;
            }
            group.put("type", "object");
            group.withObject("properties").set(parameter.path("name").asText(), copySchema(document, parameter.path("schema"), new HashSet<>()));
            if (parameter.path("required").asBoolean()) {
                required.get(location).add(parameter.path("name").asText());
            }
        }
        putGroup(properties, rootRequired, "path", groups.get("path"), required.get("path"));
        putGroup(properties, rootRequired, "query", groups.get("query"), required.get("query"));
        putGroup(properties, rootRequired, "headers", groups.get("header"), required.get("header"));
    }

    private void putGroup(ObjectNode properties, ArrayNode rootRequired, String name, ObjectNode group, ArrayNode required) {
        if (!group.has("properties")) {
            return;
        }
        if (!required.isEmpty()) {
            group.set("required", required);
            rootRequired.add(name);
        }
        properties.set(name, group);
    }

    private List<JsonNode> allParameters(JsonNode document, JsonNode pathItem, JsonNode operation) {
        Map<String, JsonNode> parameters = new LinkedHashMap<>();
        addParameters(document, pathItem.path("parameters"), parameters);
        addParameters(document, operation.path("parameters"), parameters);
        return new ArrayList<>(parameters.values());
    }

    private void addParameters(JsonNode document, JsonNode nodes, Map<String, JsonNode> target) {
        for (JsonNode candidate : nodes) {
            JsonNode parameter = resolveReference(document, candidate, new HashSet<>());
            String name = parameter.path("name").asText();
            String location = parameter.path("in").asText();
            if (name.isBlank() || location.isBlank()) {
                throw new UnsupportedOperationException("OpenAPI 参数定义无效");
            }
            target.put(location + "\n" + name, parameter);
        }
    }

    private JsonNode copySchema(JsonNode document, JsonNode schema, Set<String> seenReferences) {
        if (schema.has("oneOf") || schema.has("anyOf") || schema.has("allOf")) {
            throw new UnsupportedOperationException("当前版本不支持组合 Schema");
        }
        schema = resolveReference(document, schema, seenReferences);
        ensureSupportedSchemaKeywords(schema);
        String type = schema.path("type").asText();
        if (!(type.equals("object") || type.equals("array") || type.equals("string") || type.equals("number")
                || type.equals("integer") || type.equals("boolean"))) {
            throw new UnsupportedOperationException("当前版本不支持该 Schema 类型");
        }
        if (type.equals("object")) {
            ObjectNode copy = schema.deepCopy();
            ObjectNode copiedProperties = copy.withObject("properties");
            Iterator<Map.Entry<String, JsonNode>> fields = schema.path("properties").fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                copiedProperties.set(field.getKey(), copySchema(document, field.getValue(), new HashSet<>(seenReferences)));
            }
            return copy;
        }
        if (type.equals("array")) {
            ObjectNode copy = schema.deepCopy();
            copy.set("items", copySchema(document, schema.path("items"), seenReferences));
            return copy;
        }
        return schema.deepCopy();
    }

    private JsonNode resolveReference(JsonNode document, JsonNode node, Set<String> seenReferences) {
        if (!node.has("$ref")) {
            return node;
        }
        String reference = node.path("$ref").asText();
        if (!reference.startsWith("#/")) {
            throw new UnsupportedOperationException("当前版本不支持外部 OpenAPI 引用");
        }
        if (!seenReferences.add(reference)) {
            throw new UnsupportedOperationException("当前版本不支持递归 Schema");
        }
        JsonNode resolved = document.at(reference.substring(1));
        if (resolved.isMissingNode()) {
            throw new UnsupportedOperationException("OpenAPI 本地引用无法解析");
        }
        return resolveReference(document, resolved, seenReferences);
    }

    private void ensureSupportedSchemaKeywords(JsonNode schema) {
        for (String unsupportedKeyword : List.of("oneOf", "anyOf", "allOf", "not", "additionalProperties", "discriminator", "if", "then", "else",
                "patternProperties", "propertyNames", "contains", "prefixItems")) {
            if (schema.has(unsupportedKeyword)) {
                throw new UnsupportedOperationException("当前版本不支持复杂 Schema");
            }
        }
    }

    private String normalizePath(String path) {
        if (!path.startsWith("/")) {
            throw new GatewayException("INVALID_PATH", "OpenAPI 路径必须以 / 开头");
        }
        return path.length() > 1 && path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }
}
