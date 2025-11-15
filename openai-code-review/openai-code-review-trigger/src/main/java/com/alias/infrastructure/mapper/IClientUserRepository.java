package com.alias.infrastructure.mapper;

import com.alias.domain.model.ClientUser;
import org.apache.ibatis.annotations.Mapper;

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
     * @return the saved client user
     */
    ClientUser save(ClientUser clientUser);

    /**
     * Find client user by ID
     *
     * @param id the user ID
     * @return optional containing the client user
     */
    Optional<ClientUser> findById(UUID id);

    /**
     * Find client user by client identifier
     *
     * @param clientIdentifier the client identifier
     * @return optional containing the client user
     */
    Optional<ClientUser> findByClientIdentifier(UUID clientIdentifier);

    /**
     * Delete client user by ID
     *
     * @param id the user ID
     */
    void deleteById(UUID id);

    /**
     * Delete client user by client identifier
     *
     * @param clientIdentifier the client identifier
     */
    void deleteByClientIdentifier(UUID clientIdentifier);

    /**
     * Check if client user exists by client identifier
     *
     * @param clientIdentifier the client identifier
     * @return true if exists, false otherwise
     */
    boolean existsByClientIdentifier(UUID clientIdentifier);
}
