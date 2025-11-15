import { httpClient } from "../http";
import { ApiResponse, Conversation } from "@/types";

/**
 * 会话相关API
 */
export const conversationApi = {
  /**
   * 获取所有会话
   */
  async getConversations(): Promise<Conversation[]> {
    const response = await httpClient.get<ApiResponse<Conversation[]>>(
      "/api/v1/conversations"
    );
    return response.data.data;
  },

  /**
   * 创建新会话
   */
  async createConversation(title: string): Promise<Conversation> {
    const response = await httpClient.post<ApiResponse<Conversation>>(
      "/api/v1/conversations",
      { title }
    );
    return response.data.data;
  },

  /**
   * 删除会话
   */
  async deleteConversation(id: string): Promise<void> {
    await httpClient.delete(`/api/v1/conversations/${id}`);
  },

  /**
   * 获取会话详情
   */
  async getConversationDetail(id: string): Promise<Conversation> {
    const response = await httpClient.get<ApiResponse<Conversation>>(
      `/api/v1/conversations/${id}`
    );
    return response.data.data;
  },

  /**
   * 更新会话
   */
  async updateConversation(
    id: string,
    data: Partial<Conversation>
  ): Promise<Conversation> {
    const response = await httpClient.put<ApiResponse<Conversation>>(
      `/api/v1/conversations/${id}`,
      data
    );
    return response.data.data;
  },
};
