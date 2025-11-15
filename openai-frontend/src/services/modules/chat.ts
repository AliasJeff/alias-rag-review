import { httpClient } from "../http";
import { ApiResponse, ChatRequest, ChatResponse, ChatContext } from "@/types";

/**
 * Chat 相关 API
 */
export const chatApi = {
  /**
   * 发送聊天消息（非流式）
   */
  async chat(request: ChatRequest): Promise<ChatResponse> {
    const response = await httpClient.post<ApiResponse<ChatResponse>>(
      "/api/v1/ai-chat/chat",
      request
    );
    return response.data.data;
  },

  /**
   * 发送聊天消息（流式）
   * 返回 EventSource 用于处理 SSE
   */
  chatStream(request: ChatRequest): EventSource {
    const params = new URLSearchParams({
      message: request.message,
      userId: request.userId,
      ...(request.conversationId && { conversationId: request.conversationId }),
      ...(request.systemPrompt && { systemPrompt: request.systemPrompt }),
    });

    const baseUrl = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";
    const eventSource = new EventSource(
      `${baseUrl}/api/v1/ai-chat/chat-stream?${params.toString()}`
    );

    return eventSource;
  },

  /**
   * 获取对话上下文
   */
  async getContext(conversationId: string): Promise<ChatContext> {
    const response = await httpClient.get<ApiResponse<ChatContext>>(
      `/api/v1/ai-chat/context/${conversationId}`
    );
    return response.data.data;
  },

  /**
   * 清空对话历史
   */
  async clearContext(conversationId: string): Promise<string> {
    const response = await httpClient.delete<ApiResponse<string>>(
      `/api/v1/ai-chat/context/${conversationId}/clear`
    );
    return response.data.data;
  },

  /**
   * 删除对话
   */
  async deleteContext(conversationId: string): Promise<string> {
    const response = await httpClient.delete<ApiResponse<string>>(
      `/api/v1/ai-chat/context/${conversationId}`
    );
    return response.data.data;
  },
};
