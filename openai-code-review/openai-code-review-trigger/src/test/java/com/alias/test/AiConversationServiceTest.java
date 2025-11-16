package com.alias.test;

import com.alias.domain.model.ChatContext;
import com.alias.domain.model.ChatRequest;
import com.alias.domain.model.ChatResponse;
import com.alias.domain.service.IAiConversationService;
import com.alias.domain.service.impl.AiConversationService;
import org.junit.Before;
import org.junit.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;

/**
 * AI Conversation Service Integration Tests
 * Tests core chat functionality using Spring AI ChatResponse
 */
public class AiConversationServiceTest {

    private IAiConversationService conversationService;
    private ChatClient mockChatClient;

    @Before
    public void setUp() {
        mockChatClient = mock(ChatClient.class);
        conversationService = new AiConversationService(mockChatClient);
    }

    /**
     * Test basic chat functionality
     */
    @Test
    public void testChat() throws Exception {
        // Create chat request
        ChatRequest request = ChatRequest.builder().userId("user123").message("What is 2+2?").stream(false).build();

        // Process chat
        ChatResponse response = conversationService.chat(request);

        // Verify response
        assertNotNull(response);
        assertNotNull(response.getContent());

        // Verify context was saved
        ChatContext context = conversationService.getContext(request.getConversationId());
        assertNotNull(context);
        assertEquals(2, context.getMessageCount()); // user + assistant
        assertEquals("What is 2+2?", context.getMessages().get(0).getContent());
    }

    /**
     * Test chat with custom system prompt
     */
    @Test
    public void testChatWithSystemPrompt() throws Exception {
        // Create chat request with system prompt
        ChatRequest request = ChatRequest.builder().userId("user123").message("Hello").systemPrompt("You are a helpful assistant").stream(false).build();

        // Process chat
        ChatResponse response = conversationService.chat(request);

        // Verify response
        assertNotNull(response);
        assertNotNull(response.getContent());

        // Verify context has system prompt
        ChatContext context = conversationService.getContext(request.getConversationId());
        assertEquals("You are a helpful assistant", context.getSystemPrompt());
    }

    /**
     * Test multi-turn conversation with context preservation
     */
    @Test
    public void testMultiTurnConversation() throws Exception {
        String conversationId = UUID.randomUUID().toString();

        // First turn
        ChatRequest request1 = ChatRequest.builder().conversationId(conversationId).userId("user123").message("What is your name?").build();

        ChatResponse response1 = conversationService.chat(request1);
        assertNotNull(response1.getContent());

        // Second turn - should maintain context
        ChatRequest request2 = ChatRequest.builder().conversationId(conversationId).userId("user123").message("What did I just ask you?").build();

        ChatResponse response2 = conversationService.chat(request2);
        assertNotNull(response2.getContent());

        // Verify context has both turns
        ChatContext context = conversationService.getContext(conversationId);
        assertEquals(4, context.getMessageCount()); // 2 user + 2 assistant
        assertEquals("What is your name?", context.getMessages().get(0).getContent());
        assertEquals("What did I just ask you?", context.getMessages().get(2).getContent());
    }
}
