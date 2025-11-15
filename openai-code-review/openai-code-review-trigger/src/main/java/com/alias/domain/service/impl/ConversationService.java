package com.alias.domain.service.impl;

import com.alias.domain.model.Conversation;
import com.alias.domain.service.IConversationService;
import com.alias.infrastructure.mapper.IConversationRepository;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Conversation Service Implementation
 */
@Slf4j
@Service
public class ConversationService implements IConversationService {

    @Resource
    private IConversationRepository conversationRepository;

    @Override
    public Conversation createConversation(Conversation conversation) {
        log.info("Creating conversation for client: {}", conversation.getClientIdentifier());

        if (conversation.getId() == null) {
            conversation.setId(UUID.randomUUID());
        }

        LocalDateTime now = LocalDateTime.now();
        conversation.setCreatedAt(now);
        conversation.setUpdatedAt(now);

        if (conversation.getStatus() == null) {
            conversation.setStatus("active");
        }

        return conversationRepository.saveAndReturn(conversation);
    }

    @Override
    public Conversation getConversationById(UUID conversationId) {
        log.debug("Getting conversation by ID: {}", conversationId);
        return conversationRepository.findById(conversationId).orElse(null);
    }

    @Override
    public List<Conversation> getConversationsByClientIdentifier(UUID clientIdentifier) {
        log.debug("Getting conversations for client: {}", clientIdentifier);
        return conversationRepository.findByClientIdentifier(clientIdentifier);
    }

    @Override
    public Conversation updateConversation(Conversation conversation) {
        log.info("Updating conversation: {}", conversation.getId());
        conversation.setUpdatedAt(LocalDateTime.now());
        return conversationRepository.saveAndReturn(conversation);
    }

    @Override
    public void updateConversationStatus(UUID conversationId, String status) {
        log.info("Updating conversation status: conversationId={}, status={}", conversationId, status);

        Conversation conversation = conversationRepository.findById(conversationId).orElseThrow(() -> new IllegalArgumentException("Conversation not found: " + conversationId));

        conversation.setStatus(status);
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.saveAndReturn(conversation);
    }

    @Override
    public void deleteConversation(UUID conversationId) {
        log.info("Deleting conversation: {}", conversationId);
        conversationRepository.deleteById(conversationId);
    }

    @Override
    public List<Conversation> getConversationsByPrUrl(String prUrl) {
        log.debug("Getting conversations by PR URL: {}", prUrl);
        return conversationRepository.findByPrUrl(prUrl);
    }
}
