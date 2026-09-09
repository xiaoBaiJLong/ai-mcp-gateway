package com.lon.mcpgateway.gateway.api.tool;

import com.lon.mcpgateway.gateway.types.tool.ToolModels.CreateToolRequest;
import com.lon.mcpgateway.gateway.types.tool.ToolModels.DraftRequest;
import com.lon.mcpgateway.gateway.types.tool.ToolModels.OpenApiOperationsView;
import com.lon.mcpgateway.gateway.types.tool.ToolModels.MappingUpdateRequest;
import com.lon.mcpgateway.gateway.types.tool.ToolModels.ToolDraftView;
import com.lon.mcpgateway.gateway.types.tool.ToolModels.ToolSourceView;
import com.lon.mcpgateway.gateway.types.tool.ToolModels.ToolStatusUpdateRequest;
import com.lon.mcpgateway.gateway.types.tool.ToolModels.ToolUpdateCheckView;
import com.lon.mcpgateway.gateway.types.tool.ToolModels.ToolView;
import java.util.List;

public interface ToolImportCase {
    List<ToolSourceView> sources();

    OpenApiOperationsView operations(String serviceName);

    ToolDraftView draft(DraftRequest request);

    ToolView create(CreateToolRequest request);

    List<ToolView> tools();

    ToolView updateStatus(String toolId, ToolStatusUpdateRequest request);

    ToolUpdateCheckView checkForUpdate(String toolId);

    ToolView updateMapping(String toolId, MappingUpdateRequest request);
}
