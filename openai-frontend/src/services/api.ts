import axios, { AxiosInstance } from "axios";
import { ApiResponse, Conversation, Message, Model } from "@/types";

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
    const response = await this.client.get<ApiResponse<Conversation[]>>(
      "/conversations"
    );
    return response.data.data;
  }

  async createConversation(title: string): Promise<Conversation> {
    const response = await this.client.post<ApiResponse<Conversation>>(
      "/conversations",
      { title }
    );
    return response.data.data;
  }

  async deleteConversation(id: string): Promise<void> {
    await this.client.delete(`/conversations/${id}`);
  }

  // 消息相关接口
  async getMessages(conversationId: string): Promise<Message[]> {
    const response = await this.client.get<ApiResponse<Message[]>>(
      `/conversations/${conversationId}/messages`
    );
    return response.data.data;
  }

  async sendMessage(conversationId: string, content: string): Promise<Message> {
    const response = await this.client.post<ApiResponse<Message>>(
      `/conversations/${conversationId}/messages`,
      { content }
    );
    return response.data.data;
  }

  // 模型相关接口
  async getModels(): Promise<Model[]> {
    const response = await this.client.get<ApiResponse<Model[]>>("/models");
    return response.data.data;
  }

  // 配置相关接口
  async saveConfig(config: Record<string, any>): Promise<void> {
    await this.client.post("/config", config);
  }

  async getConfig(): Promise<Record<string, any>> {
    const response = await this.client.get<ApiResponse<Record<string, any>>>(
      "/config"
    );
    return response.data.data;
  }
}

export const apiService = new ApiService();
