package com.alias.domain.service.impl;

import com.alias.domain.model.Message;
import com.alias.domain.service.IMessageService;
import com.alias.infrastructure.mapper.IMessageRepository;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Message Service Implementation
 */
@Slf4j
@Service
public class MessageService implements IMessageService {

    @Resource
    private IMessageRepository messageRepository;

    @Override
    public Message createMessage(Message message) {
        log.debug("Creating message for conversation: {}", message.getConversationId());

        if (message.getId() == null) {
            message.setId(UUID.randomUUID());
        }

        if (message.getCreatedAt() == null) {
            message.setCreatedAt(LocalDateTime.now());
        }

        if (message.getType() == null) {
            message.setType("text");
        }

        return messageRepository.save(message);
    }

    @Override
    public Message getMessageById(UUID messageId) {
        log.debug("Getting message by ID: {}", messageId);
        return messageRepository.findById(messageId).orElse(null);
    }

    @Override
    public List<Message> getMessagesByConversationId(UUID conversationId) {
        log.debug("Getting all messages for conversation: {}", conversationId);
        return messageRepository.findByConversationId(conversationId);
    }

    @Override
    public List<Message> getMessagesByConversationId(UUID conversationId, int limit, int offset) {
        log.debug("Getting messages for conversation with pagination: conversationId={}, limit={}, offset={}", conversationId, limit, offset);
        return messageRepository.findByConversationId(conversationId, limit, offset);
    }

    @Override
    public void deleteMessage(UUID messageId) {
        log.info("Deleting message: {}", messageId);
        messageRepository.deleteById(messageId);
    }

    @Override
    public void deleteMessagesByConversationId(UUID conversationId) {
        log.info("Deleting all messages for conversation: {}", conversationId);
        messageRepository.deleteByConversationId(conversationId);
    }

    @Override
    public long getMessageCount(UUID conversationId) {
        log.debug("Getting message count for conversation: {}", conversationId);
        return messageRepository.countByConversationId(conversationId);
    }
}
