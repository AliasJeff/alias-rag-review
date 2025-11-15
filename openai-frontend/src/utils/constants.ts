// 默认配置
export const DEFAULT_CONFIG = {
  apiUrl: process.env.NEXT_PUBLIC_API_URL,
  model: "gpt-4o",
  temperature: 0.7,
  maxTokens: 2000,
};

// UI 常量
export const UI_CONSTANTS = {
  SIDEBAR_WIDTH: 280,
  MESSAGE_ANIMATION_DURATION: 300,
};

// 消息角色
export const MESSAGE_ROLES = {
  USER: "user",
  ASSISTANT: "assistant",
} as const;
