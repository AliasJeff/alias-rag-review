import { httpClient } from "../http";
import { ApiResponse, Message } from "@/types";

/**
 * 消息相关API
 */
export const messageApi = {
  /**
   * 获取会话中的所有消息
   */
  async getMessages(conversationId: string): Promise<Message[]> {
    const response = await httpClient.get<ApiResponse<Message[]>>(
      `/api/v1/conversations/${conversationId}/messages`
    );
    return response.data.data;
  },

  /**
   * 发送消息
   */
  async sendMessage(conversationId: string, content: string): Promise<Message> {
    const response = await httpClient.post<ApiResponse<Message>>(
      `/api/v1/conversations/${conversationId}/messages`,
      { content }
    );
    return response.data.data;
  },

  /**
   * 删除消息
   */
  async deleteMessage(
    conversationId: string,
    messageId: string
  ): Promise<void> {
    await httpClient.delete(
      `/api/v1/conversations/${conversationId}/messages/${messageId}`
    );
  },

  /**
   * 编辑消息
   */
  async editMessage(
    conversationId: string,
    messageId: string,
    content: string
  ): Promise<Message> {
    const response = await httpClient.put<ApiResponse<Message>>(
      `/api/v1/conversations/${conversationId}/messages/${messageId}`,
      { content }
    );
    return response.data.data;
  },
};
