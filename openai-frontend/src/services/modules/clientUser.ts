import { httpClient } from "../http";
import { ApiResponse, ClientUser } from "@/types";

/**
 * 客户端用户相关API
 */
export const clientUserApi = {
  /**
   * 获取或创建客户端用户
   */
  async getOrCreateClientUser(clientIdentifier: string): Promise<ClientUser> {
    const response = await httpClient.post<ApiResponse<ClientUser>>(
      `/api/v1/client-users/get-or-create/${clientIdentifier}`
    );
    return response.data.data;
  },

  /**
   * 根据Identifier获取客户端用户
   */
  async getClientUserByIdentifier(
    clientIdentifier: string
  ): Promise<ClientUser> {
    const response = await httpClient.get<ApiResponse<ClientUser>>(
      `/api/v1/client-users/${clientIdentifier}`
    );
    return response.data.data;
  },

  /**
   * 获取所有客户端用户
   */
  async getAllClientUsers(): Promise<ClientUser[]> {
    const response = await httpClient.get<ApiResponse<ClientUser[]>>(
      "/api/v1/client-users"
    );
    return response.data.data;
  },

  /**
   * 更新客户端用户
   */
  async updateClientUser(
    id: string,
    data: Partial<ClientUser>
  ): Promise<ClientUser> {
    const response = await httpClient.put<ApiResponse<ClientUser>>(
      `/api/v1/client-users/${id}`,
      data
    );
    return response.data.data;
  },

  /**
   * 删除客户端用户
   */
  async deleteClientUser(id: string): Promise<void> {
    await httpClient.delete(`/api/v1/client-users/${id}`);
  },

  /**
   * 更新GitHub Token
   */
  async updateGithubToken(
    clientIdentifier: string,
    githubToken: string
  ): Promise<void> {
    await httpClient.put(
      `/api/v1/client-users/${clientIdentifier}/github-token`,
      null,
      { params: { githubToken } }
    );
  },

  /**
   * 更新OpenAI API Key
   */
  async updateOpenaiApiKey(
    clientIdentifier: string,
    openaiApiKey: string
  ): Promise<void> {
    await httpClient.put(
      `/api/v1/client-users/${clientIdentifier}/openai-api-key`,
      null,
      { params: { openaiApiKey } }
    );
  },
};
