import { httpClient } from "../http";
import { ApiResponse, Conversation } from "@/types";

/**
 * 会话相关API
 */
export const conversationApi = {
  /**
   * 创建新会话
   */
  async createConversation(conversation: {
    clientIdentifier: string;
    title?: string;
    description?: string;
    prUrl?: string;
  }): Promise<Conversation> {
    const response = await httpClient.post<ApiResponse<Conversation>>(
      "/api/v1/conversations",
      conversation
    );
    return response.data.data;
  },

  /**
   * 获取会话详情
   */
  async getConversation(conversationId: string): Promise<Conversation> {
    const response = await httpClient.get<ApiResponse<Conversation>>(
      `/api/v1/conversations/${conversationId}`
    );
    return response.data.data;
  },

  /**
   * 获取客户端的所有会话
   */
  async getClientConversations(
    clientIdentifier: string
  ): Promise<Conversation[]> {
    const response = await httpClient.get<ApiResponse<Conversation[]>>(
      `/api/v1/conversations/client/${clientIdentifier}`
    );
    return response.data.data;
  },

  /**
   * 更新会话
   */
  async updateConversation(
    conversationId: string,
    data: Partial<Conversation>
  ): Promise<Conversation> {
    const response = await httpClient.put<ApiResponse<Conversation>>(
      `/api/v1/conversations/${conversationId}`,
      data
    );
    return response.data.data;
  },

  /**
   * 更新会话状态
   */
  async updateConversationStatus(
    conversationId: string,
    status: "active" | "closed" | "archived" | "error"
  ): Promise<string> {
    const response = await httpClient.patch<ApiResponse<string>>(
      `/api/v1/conversations/${conversationId}/status`,
      null,
      { params: { status } }
    );
    return response.data.data;
  },

  /**
   * 删除会话
   */
  async deleteConversation(conversationId: string): Promise<string> {
    const response = await httpClient.delete<ApiResponse<string>>(
      `/api/v1/conversations/${conversationId}`
    );
    return response.data.data;
  },

  /**
   * 根据PR URL获取会话
   */
  async getConversationsByPrUrl(prUrl: string): Promise<Conversation[]> {
    const response = await httpClient.get<ApiResponse<Conversation[]>>(
      "/api/v1/conversations/search/pr-url",
      { params: { prUrl } }
    );
    return response.data.data;
  },
};
