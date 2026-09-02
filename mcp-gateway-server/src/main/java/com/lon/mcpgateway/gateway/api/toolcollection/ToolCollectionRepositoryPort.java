package com.lon.mcpgateway.gateway.api.toolcollection;

import java.util.List;

public interface ToolCollectionRepositoryPort {
    void insert(ToolCollectionRecord collection, List<String> toolIds);

    void update(ToolCollectionRecord collection, List<String> toolIds);

    void delete(String collectionId);

    ToolCollectionRecord find(String collectionId);

    List<ToolCollectionRecord> findAll();

    List<String> findToolIds(String collectionId);
}
