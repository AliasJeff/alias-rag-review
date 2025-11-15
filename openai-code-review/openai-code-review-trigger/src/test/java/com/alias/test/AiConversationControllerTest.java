package com.alias.test;

import com.alias.domain.controller.AiConversationController;
import com.alias.domain.model.ChatContext;
import com.alias.domain.model.ChatRequest;
import com.alias.domain.model.Response;
import com.alias.domain.service.IAiConversationService;
import com.alias.domain.service.impl.AiConversationService;
import org.junit.Before;
import org.junit.Test;
import org.springframework.ai.chat.model.ChatResponse;

import java.lang.reflect.Field;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * AI Conversation Controller Integration Tests
 * Tests core controller endpoints with validation
 */
public class AiConversationControllerTest {

    private AiConversationController controller;
    private IAiConversationService mockService;

    @Before
    public void setUp() throws Exception {
        mockService = mock(IAiConversationService.class);
        controller = new AiConversationController();

        // Use reflection to inject the mock service
        Field field = AiConversationController.class.getDeclaredField("conversationService");
        field.setAccessible(true);
        field.set(controller, mockService);
    }

    /**
     * Test chat endpoint with valid request
     */
    @Test
    public void testChat_Success() throws Exception {
        // Create request
        ChatRequest request = ChatRequest.builder().userId("user123").message("Hello, AI!").build();

        // Call controller
        Response<ChatResponse> response = controller.chat(request);

        // Verify response
        assertNotNull(response);
        assertEquals("0000", response.getCode());
        assertEquals("Chat completed successfully", response.getInfo());
        assertNotNull(response.getData());

        // Verify service was called
        verify(mockService, times(1)).chat(any(ChatRequest.class));
    }

    /**
     * Test chat endpoint with missing message
     */
    @Test
    public void testChat_MissingMessage() throws Exception {
        // Create request without message
        ChatRequest request = ChatRequest.builder().userId("user123").message("").build();

        // Call controller
        Response<ChatResponse> response = controller.chat(request);

        // Verify error response
        assertNotNull(response);
        assertEquals("4000", response.getCode());
        assertEquals("Message is required", response.getInfo());
        assertNull(response.getData());

        // Verify service was not called
        verify(mockService, never()).chat(any(ChatRequest.class));
    }

    /**
     * Test chat endpoint with missing user ID
     */
    @Test
    public void testChat_MissingUserId() throws Exception {
        // Create request without user ID
        ChatRequest request = ChatRequest.builder().message("Hello").build();

        // Call controller
        Response<ChatResponse> response = controller.chat(request);

        // Verify error response
        assertNotNull(response);
        assertEquals("4000", response.getCode());
        assertEquals("User ID is required", response.getInfo());

        // Verify service was not called
        verify(mockService, never()).chat(any(ChatRequest.class));
    }

    /**
     * Test get context endpoint
     */
    @Test
    public void testGetContext_Success() {
        String conversationId = UUID.randomUUID().toString();
        ChatContext context = ChatContext.builder().conversationId(conversationId).userId("user123").title("Test Conversation").model("gpt-4o").build();

        when(mockService.getConversationHistory(conversationId)).thenReturn(context);

        // Call controller
        Response<ChatContext> response = controller.getContext(conversationId);

        // Verify response
        assertNotNull(response);
        assertEquals("0000", response.getCode());
        assertEquals("Success", response.getInfo());
        assertNotNull(response.getData());
        assertEquals(conversationId, response.getData().getConversationId());

        // Verify service was called
        verify(mockService, times(1)).getConversationHistory(conversationId);
    }

    /**
     * Test clear context endpoint
     */
    @Test
    public void testClearContext_Success() {
        String conversationId = UUID.randomUUID().toString();

        doNothing().when(mockService).clearConversation(conversationId);

        // Call controller
        Response<String> response = controller.clearContext(conversationId);

        // Verify response
        assertNotNull(response);
        assertEquals("0000", response.getCode());
        assertEquals("Conversation cleared successfully", response.getInfo());
        assertEquals(conversationId, response.getData());

        // Verify service was called
        verify(mockService, times(1)).clearConversation(conversationId);
    }

    /**
     * Test delete context endpoint
     */
    @Test
    public void testDeleteContext_Success() {
        String conversationId = UUID.randomUUID().toString();

        doNothing().when(mockService).deleteConversation(conversationId);

        // Call controller
        Response<String> response = controller.deleteContext(conversationId);

        // Verify response
        assertNotNull(response);
        assertEquals("0000", response.getCode());
        assertEquals("Conversation deleted successfully", response.getInfo());
        assertEquals(conversationId, response.getData());

        // Verify service was called
        verify(mockService, times(1)).deleteConversation(conversationId);
    }
}
