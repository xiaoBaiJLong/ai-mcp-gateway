package com.lon.mcpgateway.gateway.infrastructure.toolcollection;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
interface ToolCollectionMapper {
    @Insert("insert into tool_collections (id, name, description, created_at) values (#{id}, #{name}, #{description}, #{createdAt})")
    void insertCollection(StoredCollection collection);

    @Update("update tool_collections set name = #{name}, description = #{description} where id = #{id}")
    void updateCollection(StoredCollection collection);

    @Delete("delete from tool_collection_members where collection_id = #{collectionId}")
    void deleteMembers(String collectionId);

    @Delete("delete from tool_collections where id = #{collectionId}")
    void deleteCollection(String collectionId);

    @Insert("insert into tool_collection_members (collection_id, tool_id) values (#{collectionId}, #{toolId})")
    void insertMember(String collectionId, String toolId);

    @Select("select id, name, description, created_at as createdAt from tool_collections where id = #{collectionId}")
    CollectionRow find(String collectionId);

    @Select("select id, name, description, created_at as createdAt from tool_collections order by created_at, id")
    List<CollectionRow> findAll();

    @Select("select tool_id from tool_collection_members where collection_id = #{collectionId} order by tool_id")
    List<String> findToolIds(String collectionId);

    record StoredCollection(String id, String name, String description, Instant createdAt) {
    }

    record CollectionRow(String id, String name, String description, Instant createdAt) {
    }
}
