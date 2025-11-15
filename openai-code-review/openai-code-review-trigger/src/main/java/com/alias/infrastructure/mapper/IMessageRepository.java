package com.alias.infrastructure.mapper;

import com.alias.domain.model.Message;
import com.alias.infrastructure.typehandler.JsonbTypeHandler;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Message Repository Interface
 * Data access layer for messages
 */
@Mapper
public interface IMessageRepository {

    /**
     * Save or update a message
     *
     * @param message the message to save
     * @return number of rows affected
     */
    @Insert("INSERT INTO messages (id, conversation_id, role, type, content, metadata, created_at) " + "VALUES (#{id, javaType=java.util.UUID, jdbcType=OTHER}, " + "#{conversationId, javaType=java.util.UUID, jdbcType=OTHER}, " + "#{role, jdbcType=VARCHAR}, " + "#{type, jdbcType=VARCHAR}, " + "#{content, jdbcType=VARCHAR}, " + "#{metadata, jdbcType=OTHER, typeHandler=com.alias.infrastructure.typehandler.JsonbTypeHandler}, " + "#{createdAt, jdbcType=TIMESTAMP})")
    int save(Message message);

    /**
     * Find message by ID
     *
     * @param id the message ID
     * @return optional containing the message
     */
    @Select("SELECT id, conversation_id, role, type, content, metadata, created_at " + "FROM messages WHERE id = #{id, javaType=java.util.UUID, jdbcType=OTHER}")
    @Results(id = "messageResultMap", value = {@Result(column = "id", property = "id", javaType = UUID.class, jdbcType = org.apache.ibatis.type.JdbcType.OTHER), @Result(column = "conversation_id", property = "conversationId", javaType = UUID.class, jdbcType = org.apache.ibatis.type.JdbcType.OTHER), @Result(column = "role", property = "role", jdbcType = org.apache.ibatis.type.JdbcType.VARCHAR), @Result(column = "type", property = "type", jdbcType = org.apache.ibatis.type.JdbcType.VARCHAR), @Result(column = "content", property = "content", jdbcType = org.apache.ibatis.type.JdbcType.VARCHAR), @Result(column = "metadata", property = "metadata", jdbcType = org.apache.ibatis.type.JdbcType.OTHER, typeHandler = JsonbTypeHandler.class), @Result(column = "created_at", property = "createdAt", jdbcType = org.apache.ibatis.type.JdbcType.TIMESTAMP)
    })
    Optional<Message> findById(UUID id);

    /**
     * Find all messages for a conversation
     *
     * @param conversationId the conversation ID
     * @return list of messages
     */
    @Select("SELECT id, conversation_id, role, type, content, metadata, created_at " + "FROM messages WHERE conversation_id = #{conversationId, javaType=java.util.UUID, jdbcType=OTHER} " + "ORDER BY created_at ASC")
    @ResultMap("messageResultMap")
    List<Message> findByConversationId(UUID conversationId);

    /**
     * Find messages for a conversation with limit and offset
     *
     * @param conversationId the conversation ID
     * @param limit          the maximum number of messages
     * @param offset         the offset for pagination
     * @return list of messages
     */
    @Select("SELECT id, conversation_id, role, type, content, metadata, created_at " + "FROM messages WHERE conversation_id = #{conversationId, javaType=java.util.UUID, jdbcType=OTHER} " + "ORDER BY created_at ASC " + "LIMIT #{limit, jdbcType=INTEGER} OFFSET #{offset, jdbcType=INTEGER}")
    @ResultMap("messageResultMap")
    List<Message> findByConversationIdWithPagination(UUID conversationId, int limit, int offset);

    /**
     * Find messages by conversation ID and role
     *
     * @param conversationId the conversation ID
     * @param role           the message role
     * @return list of messages
     */
    @Select("SELECT id, conversation_id, role, type, content, metadata, created_at " + "FROM messages WHERE conversation_id = #{conversationId, javaType=java.util.UUID, jdbcType=OTHER} " + "AND role = #{role, jdbcType=VARCHAR} " + "ORDER BY created_at ASC")
    @ResultMap("messageResultMap")
    List<Message> findByConversationIdAndRole(UUID conversationId, String role);

    /**
     * Delete message by ID
     *
     * @param id the message ID
     */
    @Delete("DELETE FROM messages WHERE id = #{id, javaType=java.util.UUID, jdbcType=OTHER}")
    void deleteById(UUID id);

    /**
     * Delete all messages for a conversation
     *
     * @param conversationId the conversation ID
     */
    @Delete("DELETE FROM messages WHERE conversation_id = #{conversationId, javaType=java.util.UUID, jdbcType=OTHER}")
    void deleteByConversationId(UUID conversationId);

    /**
     * Count messages for a conversation
     *
     * @param conversationId the conversation ID
     * @return the count
     */
    @Select("SELECT COUNT(*) FROM messages WHERE conversation_id = #{conversationId, javaType=java.util.UUID, jdbcType=OTHER}")
    long countByConversationId(UUID conversationId);

    /**
     * Save or update a message and return the saved object
     *
     * @param message the message to save
     * @return the saved message
     */
    default Message saveAndReturn(Message message) {
        save(message);
        return findById(message.getId()).orElse(message);
    }
}
