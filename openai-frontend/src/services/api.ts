import axios, { AxiosInstance } from "axios";
import { ApiResponse, Conversation, Message, Model } from "@/types";

export interface ClientUser {
  id: string;
  clientIdentifier: string;
  createdAt: string;
  updatedAt: string;
}

class ApiService {
  private client: AxiosInstance;

  constructor() {
    this.client = axios.create({
      baseURL: process.env.NEXT_PUBLIC_API_URL,
      timeout: 30000,
    });
  }

  // 会话相关接口
  async getConversations(): Promise<Conversation[]> {
    return null;
  }

  async createConversation(title: string): Promise<Conversation> {
    return null;
  }

  async deleteConversation(id: string): Promise<void> {
    return;
  }

  // 消息相关接口
  async getMessages(conversationId: string): Promise<Message[]> {
    return null;
  }

  async sendMessage(conversationId: string, content: string): Promise<Message> {
    return null;
  }

  // 模型相关接口
  async getModels(): Promise<Model[]> {
    return null;
  }

  // 配置相关接口
  async saveConfig(config: Record<string, any>): Promise<void> {
    return;
  }

  async getConfig(): Promise<Record<string, any>> {
    return null;
  }

  // 客户端用户相关接口
  async getOrCreateClientUser(clientIdentifier: string): Promise<ClientUser> {
    const response = await this.client.post<ApiResponse<ClientUser>>(
      `/api/v1/client-users/get-or-create/${clientIdentifier}`
    );
    return response.data.data;
  }

  async getClientUserById(id: string): Promise<ClientUser> {
    const response = await this.client.get<ApiResponse<ClientUser>>(
      `/api/v1/client-users/${id}`
    );
    return response.data.data;
  }
}

export const apiService = new ApiService();
