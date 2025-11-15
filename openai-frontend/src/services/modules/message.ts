import { httpClient } from "../http";
import { ApiResponse, Message } from "@/types";

/**
 * 消息相关API
 */
export const messageApi = {
  /**
   * 创建消息
   */
  async createMessage(
    conversationId: string,
    role: "user" | "assistant",
    content: string
  ): Promise<Message> {
    const response = await httpClient.post<ApiResponse<Message>>(
      "/api/v1/messages",
      {
        conversationId,
        role,
        content,
      }
    );
    return response.data.data;
  },

  /**
   * 获取消息详情
   */
  async getMessage(messageId: string): Promise<Message> {
    const response = await httpClient.get<ApiResponse<Message>>(
      `/api/v1/messages/${messageId}`
    );
    return response.data.data;
  },

  /**
   * 获取会话中的所有消息
   */
  async getMessages(conversationId: string): Promise<Message[]> {
    const response = await httpClient.get<ApiResponse<Message[]>>(
      `/api/v1/messages/conversation/${conversationId}`
    );
    return response.data.data;
  },

  /**
   * 分页获取会话消息
   */
  async getMessagesPaginated(
    conversationId: string,
    limit: number = 20,
    offset: number = 0
  ): Promise<Message[]> {
    const response = await httpClient.get<ApiResponse<Message[]>>(
      `/api/v1/messages/conversation/${conversationId}/paginated`,
      { params: { limit, offset } }
    );
    return response.data.data;
  },

  /**
   * 删除消息
   */
  async deleteMessage(messageId: string): Promise<string> {
    const response = await httpClient.delete<ApiResponse<string>>(
      `/api/v1/messages/${messageId}`
    );
    return response.data.data;
  },

  /**
   * 删除会话的所有消息
   */
  async deleteConversationMessages(conversationId: string): Promise<string> {
    const response = await httpClient.delete<ApiResponse<string>>(
      `/api/v1/messages/conversation/${conversationId}`
    );
    return response.data.data;
  },

  /**
   * 获取消息数量
   */
  async getMessageCount(conversationId: string): Promise<number> {
    const response = await httpClient.get<ApiResponse<number>>(
      `/api/v1/messages/conversation/${conversationId}/count`
    );
    return response.data.data;
  },
};
