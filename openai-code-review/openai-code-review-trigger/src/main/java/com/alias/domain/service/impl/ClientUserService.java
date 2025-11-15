package com.alias.domain.service.impl;

import com.alias.domain.model.ClientUser;
import com.alias.domain.service.IClientUserService;
import com.alias.infrastructure.mapper.IClientUserRepository;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Client User Service Implementation
 */
@Slf4j
@Service
public class ClientUserService implements IClientUserService {

    @Resource
    private IClientUserRepository clientUserRepository;

    @Override
    public ClientUser getOrCreateClientUser(UUID clientIdentifier) {
        log.debug("Getting or creating client user with identifier: {}", clientIdentifier);

        return clientUserRepository.findByClientIdentifier(clientIdentifier).orElseGet(() -> {
            log.info("Creating new client user with identifier: {}", clientIdentifier);
            ClientUser newUser = ClientUser.builder().id(UUID.randomUUID()).clientIdentifier(clientIdentifier).encrypted(true).createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
            return clientUserRepository.saveAndReturn(newUser);
        });
    }

    @Override
    public ClientUser getClientUserById(UUID id) {
        log.debug("Getting client user by ID: {}", id);
        return clientUserRepository.findById(id).orElse(null);
    }

    @Override
    public ClientUser getClientUserByIdentifier(UUID clientIdentifier) {
        log.debug("Getting client user by identifier: {}", clientIdentifier);
        return clientUserRepository.findByClientIdentifier(clientIdentifier).orElse(null);
    }

    @Override
    public ClientUser saveClientUser(ClientUser clientUser) {
        log.info("Saving client user: {}", clientUser.getClientIdentifier());
        clientUser.setUpdatedAt(LocalDateTime.now());
        return clientUserRepository.saveAndReturn(clientUser);
    }

    @Override
    public void updateGithubToken(UUID clientIdentifier, String githubToken) {
        log.info("Updating GitHub token for client user: {}", clientIdentifier);
        ClientUser clientUser = clientUserRepository.findByClientIdentifier(clientIdentifier).orElseThrow(() -> new IllegalArgumentException("Client user not found: " + clientIdentifier));

        clientUser.setGithubToken(githubToken);
        clientUser.setUpdatedAt(LocalDateTime.now());
        clientUserRepository.saveAndReturn(clientUser);
    }

    @Override
    public void updateOpenaiApiKey(UUID clientIdentifier, String openaiApiKey) {
        log.info("Updating OpenAI API key for client user: {}", clientIdentifier);
        ClientUser clientUser = clientUserRepository.findByClientIdentifier(clientIdentifier).orElseThrow(() -> new IllegalArgumentException("Client user not found: " + clientIdentifier));

        clientUser.setOpenaiApiKey(openaiApiKey);
        clientUser.setUpdatedAt(LocalDateTime.now());
        clientUserRepository.saveAndReturn(clientUser);
    }

    @Override
    public void deleteClientUser(UUID clientIdentifier) {
        log.info("Deleting client user: {}", clientIdentifier);
        clientUserRepository.deleteByClientIdentifier(clientIdentifier);
    }
}
