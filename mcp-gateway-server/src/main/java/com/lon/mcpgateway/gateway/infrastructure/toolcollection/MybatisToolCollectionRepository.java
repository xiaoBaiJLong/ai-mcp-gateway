package com.lon.mcpgateway.gateway.infrastructure.toolcollection;

import com.lon.mcpgateway.gateway.api.toolcollection.ToolCollectionRepositoryPort;
import com.lon.mcpgateway.gateway.api.toolcollection.ToolCollectionRecord;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class MybatisToolCollectionRepository implements ToolCollectionRepositoryPort {
    private final ToolCollectionMapper mapper;

    public MybatisToolCollectionRepository(ToolCollectionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void insert(ToolCollectionRecord collection, List<String> toolIds) {
        mapper.insertCollection(stored(collection));
        toolIds.forEach(toolId -> mapper.insertMember(collection.id(), toolId));
    }

    @Override
    public void update(ToolCollectionRecord collection, List<String> toolIds) {
        mapper.updateCollection(stored(collection));
        mapper.deleteMembers(collection.id());
        toolIds.forEach(toolId -> mapper.insertMember(collection.id(), toolId));
    }

    @Override
    public void delete(String collectionId) {
        mapper.deleteMembers(collectionId);
        mapper.deleteCollection(collectionId);
    }

    @Override
    public ToolCollectionRecord find(String collectionId) {
        ToolCollectionMapper.CollectionRow row = mapper.find(collectionId);
        return row == null ? null : record(row);
    }

    @Override
    public List<ToolCollectionRecord> findAll() {
        return mapper.findAll().stream().map(this::record).toList();
    }

    @Override
    public List<String> findToolIds(String collectionId) {
        return mapper.findToolIds(collectionId);
    }

    private ToolCollectionMapper.StoredCollection stored(ToolCollectionRecord collection) {
        return new ToolCollectionMapper.StoredCollection(collection.id(), collection.name(), collection.description(), collection.createdAt());
    }

    private ToolCollectionRecord record(ToolCollectionMapper.CollectionRow row) {
        return new ToolCollectionRecord(row.id(), row.name(), row.description(), row.createdAt());
    }
}
