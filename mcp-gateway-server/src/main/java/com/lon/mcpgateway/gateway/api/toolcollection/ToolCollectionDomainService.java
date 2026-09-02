package com.lon.mcpgateway.gateway.api.toolcollection;

import java.util.List;

public interface ToolCollectionDomainService {
    List<ToolCollectionView> collections();

    ToolCollectionView create(ToolCollectionCommand command);

    ToolCollectionView update(String collectionId, ToolCollectionCommand command);

    void delete(String collectionId);
}
