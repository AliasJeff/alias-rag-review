// 会话类型
export interface Conversation {
  id: string;
  title: string;
  createdAt: string;
  updatedAt: string;
}

// 客户端用户类型
export interface ClientUser {
  id: string;
  clientIdentifier: string;
  githubToken?: string;
  openaiApiKey?: string;
  model?: string;
  temperature?: number;
  maxTokens?: number;
  createdAt: string;
  updatedAt: string;
}

// 消息类型
export interface Message {
  id: string;
  conversationId: string;
  role: "user" | "assistant";
  content: string;
  createdAt: string;
}

// API 响应类型
export interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
}

// 模型信息
export interface Model {
  id: string;
  name: string;
  description?: string;
}

// 配置信息
export interface Config {
  apiKey?: string;
  apiUrl?: string;
  model?: string;
  temperature?: number;
  maxTokens?: number;
}
