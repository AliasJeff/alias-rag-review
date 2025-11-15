package com.alias.domain.service;

import com.alias.domain.model.ClientUser;

import java.util.UUID;

/**
 * Client User Service Interface
 * Manages client user operations
 */
public interface IClientUserService {

    /**
     * Get or create a client user by client identifier
     *
     * @param clientIdentifier the client identifier UUID
     * @return the client user
     */
    ClientUser getOrCreateClientUser(UUID clientIdentifier);

    /**
     * Get client user by ID
     *
     * @param id the user ID
     * @return the client user or null if not found
     */
    ClientUser getClientUserById(UUID id);

    /**
     * Get client user by client identifier
     *
     * @param clientIdentifier the client identifier
     * @return the client user or null if not found
     */
    ClientUser getClientUserByIdentifier(UUID clientIdentifier);

    /**
     * Save or update client user
     *
     * @param clientUser the client user to save
     * @return the saved client user
     */
    ClientUser saveClientUser(ClientUser clientUser);

    /**
     * Update GitHub token for a client user
     *
     * @param clientIdentifier the client identifier
     * @param githubToken      the encrypted GitHub token
     */
    void updateGithubToken(UUID clientIdentifier, String githubToken);

    /**
     * Update OpenAI API key for a client user
     *
     * @param clientIdentifier the client identifier
     * @param openaiApiKey     the encrypted OpenAI API key
     */
    void updateOpenaiApiKey(UUID clientIdentifier, String openaiApiKey);

    /**
     * Delete client user
     *
     * @param clientIdentifier the client identifier
     */
    void deleteClientUser(UUID clientIdentifier);
}
