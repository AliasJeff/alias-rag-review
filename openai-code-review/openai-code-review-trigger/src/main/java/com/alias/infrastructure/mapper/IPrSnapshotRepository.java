package com.alias.infrastructure.mapper;

import com.alias.domain.model.PrSnapshot;
import com.alias.infrastructure.typehandler.JsonbTypeHandler;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * PR Snapshot Repository Interface
 * Data access layer for PR snapshots
 */
@Mapper
public interface IPrSnapshotRepository {

    /**
     * Save or update a PR snapshot
     *
     * @param snapshot the snapshot to save
     * @return number of rows affected
     */
    @Insert("INSERT INTO pr_snapshots (id, conversation_id, file_path, diff, content_before, content_after, metadata, created_at) " + "VALUES (#{id, javaType=java.util.UUID, jdbcType=OTHER}, " + "#{conversationId, javaType=java.util.UUID, jdbcType=OTHER}, " + "#{filePath, jdbcType=VARCHAR}, " + "#{diff, jdbcType=VARCHAR}, " + "#{contentBefore, jdbcType=VARCHAR}, " + "#{contentAfter, jdbcType=VARCHAR}, " + "#{metadata, jdbcType=OTHER, typeHandler=com.alias.infrastructure.typehandler.JsonbTypeHandler}, " + "#{createdAt, jdbcType=TIMESTAMP})")
    int save(PrSnapshot snapshot);

    /**
     * Find snapshot by ID
     *
     * @param id the snapshot ID
     * @return optional containing the snapshot
     */
    @Select("SELECT id, conversation_id, file_path, diff, content_before, content_after, metadata, created_at " + "FROM pr_snapshots WHERE id = #{id, javaType=java.util.UUID, jdbcType=OTHER}")
    @Results(id = "prSnapshotResultMap", value = {@Result(column = "id", property = "id", javaType = UUID.class, jdbcType = org.apache.ibatis.type.JdbcType.OTHER), @Result(column = "conversation_id", property = "conversationId", javaType = UUID.class, jdbcType = org.apache.ibatis.type.JdbcType.OTHER), @Result(column = "file_path", property = "filePath", jdbcType = org.apache.ibatis.type.JdbcType.VARCHAR), @Result(column = "diff", property = "diff", jdbcType = org.apache.ibatis.type.JdbcType.VARCHAR), @Result(column = "content_before", property = "contentBefore", jdbcType = org.apache.ibatis.type.JdbcType.VARCHAR), @Result(column = "content_after", property = "contentAfter", jdbcType = org.apache.ibatis.type.JdbcType.VARCHAR), @Result(column = "metadata", property = "metadata", jdbcType = org.apache.ibatis.type.JdbcType.OTHER, typeHandler = JsonbTypeHandler.class), @Result(column = "created_at", property = "createdAt", jdbcType = org.apache.ibatis.type.JdbcType.TIMESTAMP)
    })
    Optional<PrSnapshot> findById(UUID id);

    /**
     * Find all snapshots for a conversation
     *
     * @param conversationId the conversation ID
     * @return list of snapshots
     */
    @Select("SELECT id, conversation_id, file_path, diff, content_before, content_after, metadata, created_at " + "FROM pr_snapshots WHERE conversation_id = #{conversationId, javaType=java.util.UUID, jdbcType=OTHER} " + "ORDER BY created_at DESC")
    @ResultMap("prSnapshotResultMap")
    List<PrSnapshot> findByConversationId(UUID conversationId);

    /**
     * Find snapshot by conversation ID and file path
     *
     * @param conversationId the conversation ID
     * @param filePath       the file path
     * @return optional containing the snapshot
     */
    @Select("SELECT id, conversation_id, file_path, diff, content_before, content_after, metadata, created_at " + "FROM pr_snapshots WHERE conversation_id = #{conversationId, javaType=java.util.UUID, jdbcType=OTHER} " + "AND file_path = #{filePath, jdbcType=VARCHAR} " + "LIMIT 1")
    @ResultMap("prSnapshotResultMap")
    Optional<PrSnapshot> findByConversationIdAndFilePath(UUID conversationId, String filePath);

    /**
     * Find snapshots by file path pattern (using LIKE)
     *
     * @param conversationId  the conversation ID
     * @param filePathPattern the file path pattern
     * @return list of matching snapshots
     */
    @Select("SELECT id, conversation_id, file_path, diff, content_before, content_after, metadata, created_at " + "FROM pr_snapshots WHERE conversation_id = #{conversationId, javaType=java.util.UUID, jdbcType=OTHER} " + "AND file_path LIKE #{filePathPattern, jdbcType=VARCHAR} " + "ORDER BY created_at DESC")
    @ResultMap("prSnapshotResultMap")
    List<PrSnapshot> findByConversationIdAndFilePathLike(UUID conversationId, String filePathPattern);

    /**
     * Delete snapshot by ID
     *
     * @param id the snapshot ID
     */
    @Delete("DELETE FROM pr_snapshots WHERE id = #{id, javaType=java.util.UUID, jdbcType=OTHER}")
    void deleteById(UUID id);

    /**
     * Delete all snapshots for a conversation
     *
     * @param conversationId the conversation ID
     */
    @Delete("DELETE FROM pr_snapshots WHERE conversation_id = #{conversationId, javaType=java.util.UUID, jdbcType=OTHER}")
    void deleteByConversationId(UUID conversationId);

    /**
     * Count snapshots for a conversation
     *
     * @param conversationId the conversation ID
     * @return the count
     */
    @Select("SELECT COUNT(*) FROM pr_snapshots WHERE conversation_id = #{conversationId, javaType=java.util.UUID, jdbcType=OTHER}")
    long countByConversationId(UUID conversationId);

    /**
     * Save or update a PR snapshot and return the saved object
     *
     * @param snapshot the snapshot to save
     * @return the saved snapshot
     */
    default PrSnapshot saveAndReturn(PrSnapshot snapshot) {
        save(snapshot);
        return findById(snapshot.getId()).orElse(snapshot);
    }
}
