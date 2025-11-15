package com.alias.infrastructure.mapper;

import com.alias.domain.model.Conversation;
import com.alias.infrastructure.typehandler.JsonbTypeHandler;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Conversation Repository Interface
 * Data access layer for conversations
 */
@Mapper
public interface IConversationRepository {

    /**
     * Save or update a conversation
     *
     * @param conversation the conversation to save
     * @return number of rows affected
     */
    @Insert("INSERT INTO conversations (id, client_identifier, title, pr_url, repo, pr_number, status, metadata, created_at, updated_at) " + "VALUES (#{id, javaType=java.util.UUID, jdbcType=OTHER}, " + "#{clientIdentifier, javaType=java.util.UUID, jdbcType=OTHER}, " + "#{title, jdbcType=VARCHAR}, " + "#{prUrl, jdbcType=VARCHAR}, " + "#{repo, jdbcType=VARCHAR}, " + "#{prNumber, jdbcType=INTEGER}, " + "#{status, jdbcType=VARCHAR}, " + "#{metadata, jdbcType=OTHER, typeHandler=com.alias.infrastructure.typehandler.JsonbTypeHandler}, " + "#{createdAt, jdbcType=TIMESTAMP}, " + "#{updatedAt, jdbcType=TIMESTAMP}) " + "ON CONFLICT (id) DO UPDATE SET " + "title = EXCLUDED.title, " + "pr_url = EXCLUDED.pr_url, " + "repo = EXCLUDED.repo, " + "pr_number = EXCLUDED.pr_number, " + "status = EXCLUDED.status, " + "metadata = EXCLUDED.metadata, " + "updated_at = EXCLUDED.updated_at")
    int save(Conversation conversation);

    /**
     * Find conversation by ID
     *
     * @param id the conversation ID
     * @return optional containing the conversation
     */
    @Select("SELECT id, client_identifier, title, pr_url, repo, pr_number, status, metadata, created_at, updated_at " + "FROM conversations WHERE id = #{id, javaType=java.util.UUID, jdbcType=OTHER}")
    @Results(id = "conversationResultMap", value = {@Result(column = "id", property = "id", javaType = UUID.class, jdbcType = org.apache.ibatis.type.JdbcType.OTHER), @Result(column = "client_identifier", property = "clientIdentifier", javaType = UUID.class, jdbcType = org.apache.ibatis.type.JdbcType.OTHER), @Result(column = "title", property = "title", jdbcType = org.apache.ibatis.type.JdbcType.VARCHAR), @Result(column = "pr_url", property = "prUrl", jdbcType = org.apache.ibatis.type.JdbcType.VARCHAR), @Result(column = "repo", property = "repo", jdbcType = org.apache.ibatis.type.JdbcType.VARCHAR), @Result(column = "pr_number", property = "prNumber", jdbcType = org.apache.ibatis.type.JdbcType.INTEGER), @Result(column = "status", property = "status", jdbcType = org.apache.ibatis.type.JdbcType.VARCHAR), @Result(column = "metadata", property = "metadata", jdbcType = org.apache.ibatis.type.JdbcType.OTHER, typeHandler = JsonbTypeHandler.class), @Result(column = "created_at", property = "createdAt", jdbcType = org.apache.ibatis.type.JdbcType.TIMESTAMP), @Result(column = "updated_at", property = "updatedAt", jdbcType = org.apache.ibatis.type.JdbcType.TIMESTAMP)
    })
    Optional<Conversation> findById(UUID id);

    /**
     * Find all conversations for a client identifier
     *
     * @param clientIdentifier the client identifier
     * @return list of conversations
     */
    @Select("SELECT id, client_identifier, title, pr_url, repo, pr_number, status, metadata, created_at, updated_at " + "FROM conversations WHERE client_identifier = #{clientIdentifier, javaType=java.util.UUID, jdbcType=OTHER} " + "ORDER BY created_at DESC")
    @ResultMap("conversationResultMap")
    List<Conversation> findByClientIdentifier(UUID clientIdentifier);

    /**
     * Find conversations by PR URL
     *
     * @param prUrl the PR URL
     * @return list of conversations
     */
    @Select("SELECT id, client_identifier, title, pr_url, repo, pr_number, status, metadata, created_at, updated_at " + "FROM conversations WHERE pr_url LIKE #{prUrl, jdbcType=VARCHAR} " + "ORDER BY created_at DESC")
    @ResultMap("conversationResultMap")
    List<Conversation> findByPrUrl(String prUrl);

    /**
     * Find conversations by status
     *
     * @param status the conversation status
     * @return list of conversations
     */
    @Select("SELECT id, client_identifier, title, pr_url, repo, pr_number, status, metadata, created_at, updated_at " + "FROM conversations WHERE status = #{status, jdbcType=VARCHAR} " + "ORDER BY created_at DESC")
    @ResultMap("conversationResultMap")
    List<Conversation> findByStatus(String status);

    /**
     * Delete conversation by ID
     *
     * @param id the conversation ID
     */
    @Delete("DELETE FROM conversations WHERE id = #{id, javaType=java.util.UUID, jdbcType=OTHER}")
    void deleteById(UUID id);

    /**
     * Delete all conversations for a client identifier
     *
     * @param clientIdentifier the client identifier
     */
    @Delete("DELETE FROM conversations WHERE client_identifier = #{clientIdentifier, javaType=java.util.UUID, jdbcType=OTHER}")
    void deleteByClientIdentifier(UUID clientIdentifier);

    /**
     * Count conversations for a client identifier
     *
     * @param clientIdentifier the client identifier
     * @return the count
     */
    @Select("SELECT COUNT(*) FROM conversations WHERE client_identifier = #{clientIdentifier, javaType=java.util.UUID, jdbcType=OTHER}")
    long countByClientIdentifier(UUID clientIdentifier);

    /**
     * Save or update a conversation and return the saved object
     *
     * @param conversation the conversation to save
     * @return the saved conversation
     */
    default Conversation saveAndReturn(Conversation conversation) {
        save(conversation);
        return findById(conversation.getId()).orElse(conversation);
    }
}
