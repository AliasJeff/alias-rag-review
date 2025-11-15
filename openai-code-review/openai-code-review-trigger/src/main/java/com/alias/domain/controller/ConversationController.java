package com.alias.domain.controller;

import com.alias.domain.model.Conversation;
import com.alias.domain.model.Response;
import com.alias.domain.service.IConversationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Conversation Controller
 * Handles conversation management operations
 */
@Slf4j
@Tag(name = "对话管理接口", description = "管理用户对话的接口")
@RestController
@CrossOrigin("*")
@RequestMapping("/api/v1/conversations")
public class ConversationController {

    @Resource
    private IConversationService conversationService;

    /**
     * Create a new conversation
     *
     * @param conversation the conversation to create
     * @return created conversation
     */
    @Operation(summary = "创建对话", description = "为客户端用户创建新的对话")
    @PostMapping
    public Response<Conversation> createConversation(@RequestBody Conversation conversation) {
        try {
            if (conversation.getClientIdentifier() == null) {
                return Response.<Conversation>builder().code("4000").info("Client identifier is required").build();
            }

            log.info("Creating conversation for client: {}", conversation.getClientIdentifier());
            Conversation created = conversationService.createConversation(conversation);

            return Response.<Conversation>builder().code("0000").info("Conversation created successfully").data(created).build();
        } catch (Exception e) {
            log.error("Failed to create conversation", e);
            return Response.<Conversation>builder().code("5000").info("Failed: " + e.getMessage()).build();
        }
    }

    /**
     * Get conversation by ID
     *
     * @param conversationId the conversation ID
     * @return conversation
     */
    @Operation(summary = "获取对话", description = "根据对话ID获取对话详情")
    @GetMapping("/{conversationId}")
    public Response<Conversation> getConversation(@PathVariable("conversationId") UUID conversationId) {
        try {
            log.info("Getting conversation: {}", conversationId);
            Conversation conversation = conversationService.getConversationById(conversationId);

            if (conversation == null) {
                return Response.<Conversation>builder().code("4004").info("Conversation not found").build();
            }

            return Response.<Conversation>builder().code("0000").info("Success").data(conversation).build();
        } catch (Exception e) {
            log.error("Failed to get conversation: {}", conversationId, e);
            return Response.<Conversation>builder().code("5000").info("Failed: " + e.getMessage()).build();
        }
    }

    /**
     * Get all conversations for a client
     *
     * @param clientIdentifier the client identifier
     * @return list of conversations
     */
    @Operation(summary = "获取客户端的所有对话", description = "获取指定客户端用户的所有对话列表")
    @GetMapping("/client/{clientIdentifier}")
    public Response<List<Conversation>> getClientConversations(@PathVariable("clientIdentifier") UUID clientIdentifier) {
        try {
            log.info("Getting conversations for client: {}", clientIdentifier);
            List<Conversation> conversations = conversationService.getConversationsByClientIdentifier(clientIdentifier);

            return Response.<List<Conversation>>builder().code("0000").info("Success").data(conversations).build();
        } catch (Exception e) {
            log.error("Failed to get client conversations: {}", clientIdentifier, e);
            return Response.<List<Conversation>>builder().code("5000").info("Failed: " + e.getMessage()).build();
        }
    }

    /**
     * Update conversation
     *
     * @param conversationId the conversation ID
     * @param conversation   the updated conversation
     * @return updated conversation
     */
    @Operation(summary = "更新对话", description = "更新对话的信息")
    @PutMapping("/{conversationId}")
    public Response<Conversation> updateConversation(
                                                     @PathVariable("conversationId") UUID conversationId, @RequestBody Conversation conversation) {
        try {
            conversation.setId(conversationId);
            log.info("Updating conversation: {}", conversationId);
            Conversation updated = conversationService.updateConversation(conversation);

            return Response.<Conversation>builder().code("0000").info("Conversation updated successfully").data(updated).build();
        } catch (Exception e) {
            log.error("Failed to update conversation: {}", conversationId, e);
            return Response.<Conversation>builder().code("5000").info("Failed: " + e.getMessage()).build();
        }
    }

    /**
     * Update conversation status
     *
     * @param conversationId the conversation ID
     * @param status         the new status
     * @return response
     */
    @Operation(summary = "更新对话状态", description = "更新对话的状态（active/closed/archived/error）")
    @PatchMapping("/{conversationId}/status")
    public Response<String> updateConversationStatus(
                                                     @PathVariable("conversationId") UUID conversationId, @RequestParam("status") String status) {
        try {
            log.info("Updating conversation status: conversationId={}, status={}", conversationId, status);
            conversationService.updateConversationStatus(conversationId, status);

            return Response.<String>builder().code("0000").info("Conversation status updated successfully").data(status).build();
        } catch (Exception e) {
            log.error("Failed to update conversation status: {}", conversationId, e);
            return Response.<String>builder().code("5000").info("Failed: " + e.getMessage()).build();
        }
    }

    /**
     * Delete conversation
     *
     * @param conversationId the conversation ID
     * @return response
     */
    @Operation(summary = "删除对话", description = "删除指定的对话及其所有消息")
    @DeleteMapping("/{conversationId}")
    public Response<String> deleteConversation(@PathVariable("conversationId") UUID conversationId) {
        try {
            log.info("Deleting conversation: {}", conversationId);
            conversationService.deleteConversation(conversationId);

            return Response.<String>builder().code("0000").info("Conversation deleted successfully").data(conversationId.toString()).build();
        } catch (Exception e) {
            log.error("Failed to delete conversation: {}", conversationId, e);
            return Response.<String>builder().code("5000").info("Failed: " + e.getMessage()).build();
        }
    }

    /**
     * Get conversations by PR URL
     *
     * @param prUrl the PR URL
     * @return list of conversations
     */
    @Operation(summary = "根据PR URL获取对话", description = "获取与指定PR URL关联的所有对话")
    @GetMapping("/search/pr-url")
    public Response<List<Conversation>> getConversationsByPrUrl(@RequestParam("prUrl") String prUrl) {
        try {
            log.info("Getting conversations by PR URL: {}", prUrl);
            List<Conversation> conversations = conversationService.getConversationsByPrUrl(prUrl);

            return Response.<List<Conversation>>builder().code("0000").info("Success").data(conversations).build();
        } catch (Exception e) {
            log.error("Failed to get conversations by PR URL: {}", prUrl, e);
            return Response.<List<Conversation>>builder().code("5000").info("Failed: " + e.getMessage()).build();
        }
    }
}
