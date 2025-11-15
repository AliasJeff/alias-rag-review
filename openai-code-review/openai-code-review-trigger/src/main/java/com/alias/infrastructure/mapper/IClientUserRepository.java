package com.alias.infrastructure.mapper;

import com.alias.domain.model.ClientUser;
import com.alias.infrastructure.typehandler.JsonbTypeHandler;
import org.apache.ibatis.annotations.*;

import java.util.Optional;
import java.util.UUID;

/**
 * Client User Repository Interface
 * Data access layer for client users
 */
@Mapper
public interface IClientUserRepository {

    /**
     * Save or update a client user
     *
     * @param clientUser the client user to save
     * @return number of rows affected
     */
    @Insert("INSERT INTO client_users (id, client_identifier, github_token, openai_api_key, encrypted, metadata, created_at, updated_at) " + "VALUES (#{id, javaType=java.util.UUID, jdbcType=OTHER}, " + "#{clientIdentifier, javaType=java.util.UUID, jdbcType=OTHER}, " + "#{githubToken, jdbcType=VARCHAR}, " + "#{openaiApiKey, jdbcType=VARCHAR}, " + "#{encrypted, jdbcType=BOOLEAN}, " + "#{metadata, jdbcType=OTHER, typeHandler=com.alias.infrastructure.typehandler.JsonbTypeHandler}, " + "#{createdAt, jdbcType=TIMESTAMP}, " + "#{updatedAt, jdbcType=TIMESTAMP}) " + "ON CONFLICT (client_identifier) DO UPDATE SET " + "github_token = EXCLUDED.github_token, " + "openai_api_key = EXCLUDED.openai_api_key, " + "encrypted = EXCLUDED.encrypted, " + "metadata = EXCLUDED.metadata, " + "updated_at = EXCLUDED.updated_at")
    int save(ClientUser clientUser);

    /**
     * Find client user by ID
     *
     * @param id the user ID
     * @return optional containing the client user
     */
    @Select("SELECT id, client_identifier, github_token, openai_api_key, encrypted, metadata, created_at, updated_at " + "FROM client_users WHERE id = #{id, javaType=java.util.UUID, jdbcType=OTHER}")
    @Results(id = "clientUserResultMap", value = {@Result(column = "id", property = "id", javaType = UUID.class, jdbcType = org.apache.ibatis.type.JdbcType.OTHER), @Result(column = "client_identifier", property = "clientIdentifier", javaType = UUID.class, jdbcType = org.apache.ibatis.type.JdbcType.OTHER), @Result(column = "github_token", property = "githubToken", jdbcType = org.apache.ibatis.type.JdbcType.VARCHAR), @Result(column = "openai_api_key", property = "openaiApiKey", jdbcType = org.apache.ibatis.type.JdbcType.VARCHAR), @Result(column = "encrypted", property = "encrypted", jdbcType = org.apache.ibatis.type.JdbcType.BOOLEAN), @Result(column = "metadata", property = "metadata", jdbcType = org.apache.ibatis.type.JdbcType.OTHER, typeHandler = JsonbTypeHandler.class), @Result(column = "created_at", property = "createdAt", jdbcType = org.apache.ibatis.type.JdbcType.TIMESTAMP), @Result(column = "updated_at", property = "updatedAt", jdbcType = org.apache.ibatis.type.JdbcType.TIMESTAMP)
    })
    Optional<ClientUser> findById(UUID id);

    /**
     * Save or update a client user and return the saved object
     *
     * @param clientUser the client user to save
     * @return the saved client user
     */
    default ClientUser saveAndReturn(ClientUser clientUser) {
        save(clientUser);
        return findById(clientUser.getId()).orElse(clientUser);
    }

    /**
     * Find client user by client identifier
     *
     * @param clientIdentifier the client identifier
     * @return optional containing the client user
     */
    @Select("SELECT id, client_identifier, github_token, openai_api_key, encrypted, metadata, created_at, updated_at " + "FROM client_users WHERE client_identifier = #{clientIdentifier, javaType=java.util.UUID, jdbcType=OTHER}")
    @ResultMap("clientUserResultMap")
    Optional<ClientUser> findByClientIdentifier(UUID clientIdentifier);

    /**
     * Delete client user by ID
     *
     * @param id the user ID
     */
    @Delete("DELETE FROM client_users WHERE id = #{id, javaType=java.util.UUID, jdbcType=OTHER}")
    void deleteById(UUID id);

    /**
     * Delete client user by client identifier
     *
     * @param clientIdentifier the client identifier
     */
    @Delete("DELETE FROM client_users WHERE client_identifier = #{clientIdentifier, javaType=java.util.UUID, jdbcType=OTHER}")
    void deleteByClientIdentifier(UUID clientIdentifier);

    /**
     * Check if client user exists by client identifier
     *
     * @param clientIdentifier the client identifier
     * @return true if exists, false otherwise
     */
    @Select("SELECT EXISTS(SELECT 1 FROM client_users WHERE client_identifier = #{clientIdentifier, javaType=java.util.UUID, jdbcType=OTHER})")
    boolean existsByClientIdentifier(UUID clientIdentifier);
}
