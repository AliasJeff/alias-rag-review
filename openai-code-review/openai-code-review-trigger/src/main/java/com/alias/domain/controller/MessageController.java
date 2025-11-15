package com.alias.domain.controller;

import com.alias.domain.model.Message;
import com.alias.domain.model.Response;
import com.alias.domain.service.IMessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Message Controller
 * Handles message management operations
 */
@Slf4j
@Tag(name = "消息管理接口", description = "管理对话消息的接口")
@RestController
@CrossOrigin("*")
@RequestMapping("/api/v1/messages")
public class MessageController {

    @Resource
    private IMessageService messageService;

    /**
     * Create a new message
     *
     * @param message the message to create
     * @return created message
     */
    @Operation(summary = "创建消息", description = "在对话中创建新消息")
    @PostMapping
    public Response<Message> createMessage(@RequestBody Message message) {
        try {
            if (message.getConversationId() == null) {
                return Response.<Message>builder().code("4000").info("Conversation ID is required").build();
            }

            if (message.getRole() == null || message.getRole().isEmpty()) {
                return Response.<Message>builder().code("4000").info("Message role is required").build();
            }

            if (message.getContent() == null || message.getContent().isEmpty()) {
                return Response.<Message>builder().code("4000").info("Message content is required").build();
            }

            log.info("Creating message for conversation: {}", message.getConversationId());
            Message created = messageService.createMessage(message);

            return Response.<Message>builder().code("0000").info("Message created successfully").data(created).build();
        } catch (Exception e) {
            log.error("Failed to create message", e);
            return Response.<Message>builder().code("5000").info("Failed: " + e.getMessage()).build();
        }
    }

    /**
     * Get message by ID
     *
     * @param messageId the message ID
     * @return message
     */
    @Operation(summary = "获取消息", description = "根据消息ID获取消息详情")
    @GetMapping("/{messageId}")
    public Response<Message> getMessage(@PathVariable("messageId") UUID messageId) {
        try {
            log.info("Getting message: {}", messageId);
            Message message = messageService.getMessageById(messageId);

            if (message == null) {
                return Response.<Message>builder().code("4004").info("Message not found").build();
            }

            return Response.<Message>builder().code("0000").info("Success").data(message).build();
        } catch (Exception e) {
            log.error("Failed to get message: {}", messageId, e);
            return Response.<Message>builder().code("5000").info("Failed: " + e.getMessage()).build();
        }
    }

    /**
     * Get all messages for a conversation
     *
     * @param conversationId the conversation ID
     * @return list of messages
     */
    @Operation(summary = "获取对话的所有消息", description = "获取指定对话的所有消息列表")
    @GetMapping("/conversation/{conversationId}")
    public Response<List<Message>> getConversationMessages(@PathVariable("conversationId") UUID conversationId) {
        try {
            log.info("Getting messages for conversation: {}", conversationId);
            List<Message> messages = messageService.getMessagesByConversationId(conversationId);

            return Response.<List<Message>>builder().code("0000").info("Success").data(messages).build();
        } catch (Exception e) {
            log.error("Failed to get conversation messages: {}", conversationId, e);
            return Response.<List<Message>>builder().code("5000").info("Failed: " + e.getMessage()).build();
        }
    }

    /**
     * Get messages for a conversation with pagination
     *
     * @param conversationId the conversation ID
     * @param limit          the maximum number of messages
     * @param offset         the offset for pagination
     * @return list of messages
     */
    @Operation(summary = "分页获取对话消息", description = "分页获取指定对话的消息列表")
    @GetMapping("/conversation/{conversationId}/paginated")
    public Response<List<Message>> getConversationMessagesPaginated(
                                                                    @PathVariable("conversationId") UUID conversationId, @RequestParam(value = "limit", defaultValue = "20") int limit, @RequestParam(value = "offset", defaultValue = "0") int offset) {
        try {
            log.info("Getting paginated messages for conversation: conversationId={}, limit={}, offset={}", conversationId, limit, offset);
            List<Message> messages = messageService.getMessagesByConversationId(conversationId, limit, offset);

            return Response.<List<Message>>builder().code("0000").info("Success").data(messages).build();
        } catch (Exception e) {
            log.error("Failed to get paginated conversation messages: {}", conversationId, e);
            return Response.<List<Message>>builder().code("5000").info("Failed: " + e.getMessage()).build();
        }
    }

    /**
     * Delete message
     *
     * @param messageId the message ID
     * @return response
     */
    @Operation(summary = "删除消息", description = "删除指定的消息")
    @DeleteMapping("/{messageId}")
    public Response<String> deleteMessage(@PathVariable("messageId") UUID messageId) {
        try {
            log.info("Deleting message: {}", messageId);
            messageService.deleteMessage(messageId);

            return Response.<String>builder().code("0000").info("Message deleted successfully").data(messageId.toString()).build();
        } catch (Exception e) {
            log.error("Failed to delete message: {}", messageId, e);
            return Response.<String>builder().code("5000").info("Failed: " + e.getMessage()).build();
        }
    }

    /**
     * Delete all messages for a conversation
     *
     * @param conversationId the conversation ID
     * @return response
     */
    @Operation(summary = "清空对话消息", description = "删除指定对话的所有消息")
    @DeleteMapping("/conversation/{conversationId}")
    public Response<String> deleteConversationMessages(@PathVariable("conversationId") UUID conversationId) {
        try {
            log.info("Deleting all messages for conversation: {}", conversationId);
            messageService.deleteMessagesByConversationId(conversationId);

            return Response.<String>builder().code("0000").info("Conversation messages deleted successfully").data(conversationId.toString()).build();
        } catch (Exception e) {
            log.error("Failed to delete conversation messages: {}", conversationId, e);
            return Response.<String>builder().code("5000").info("Failed: " + e.getMessage()).build();
        }
    }

    /**
     * Get message count for a conversation
     *
     * @param conversationId the conversation ID
     * @return message count
     */
    @Operation(summary = "获取对话消息数", description = "获取指定对话的消息总数")
    @GetMapping("/conversation/{conversationId}/count")
    public Response<Long> getMessageCount(@PathVariable("conversationId") UUID conversationId) {
        try {
            log.info("Getting message count for conversation: {}", conversationId);
            long count = messageService.getMessageCount(conversationId);

            return Response.<Long>builder().code("0000").info("Success").data(count).build();
        } catch (Exception e) {
            log.error("Failed to get message count: {}", conversationId, e);
            return Response.<Long>builder().code("5000").info("Failed: " + e.getMessage()).build();
        }
    }
}
