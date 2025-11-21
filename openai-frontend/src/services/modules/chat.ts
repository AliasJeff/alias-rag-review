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
   * 使用 fetch + ReadableStream 处理流式响应
   */
  async chatStream(
    request: ChatRequest,
    onChunk?: (chunk: string) => void,
    onError?: (error: string) => void,
    onComplete?: () => void
  ): Promise<void> {
    const baseUrl = process.env.NEXT_PUBLIC_API_URL;

    try {
      const response = await fetch(
        `${baseUrl}/api/v1/ai-chat/chat-stream-router`,
        {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
          },
          body: JSON.stringify(request),
        }
      );

      if (!response.ok) {
        const error = `HTTP ${response.status}: ${response.statusText}`;
        onError?.(error);
        throw new Error(error);
      }

      const reader = response.body?.getReader();
      if (!reader) {
        const error = "Response body is not readable";
        onError?.(error);
        throw new Error(error);
      }

      const decoder = new TextDecoder();
      let buffer = "";

      while (true) {
        const { done, value } = await reader.read();
        if (done) {
          onComplete?.();
          break;
        }

        buffer += decoder.decode(value, { stream: true });

        // SSE 事件按双换行分割
        const events = buffer.split(/\r?\n\r?\n/);

        // 保留最后一段（可能未完整）
        buffer = events.pop() || "";

        for (const event of events) {
          const lines = event.split(/\r?\n/);

          for (const line of lines) {
            // 只处理 data 行
            if (line.startsWith("data:")) {
              const jsonStr = line.substring(5).trim();

              // 特殊结束标记
              if (jsonStr === "Streaming completed") {
                onComplete?.();
                continue;
              }

              // 尝试解析 JSON
              let parsed: { content?: string } | null = null;
              try {
                parsed = JSON.parse(jsonStr);
              } catch (_) {
                // 如果不是 JSON，直接作为普通文本处理
                onChunk?.(jsonStr);
                continue;
              }

              // 有 content 字段时再处理
              if (parsed && parsed.content) {
                onChunk?.(JSON.stringify(parsed));
              }
            }
          }
        }
      }
    } catch (error) {
      const errorMsg =
        error instanceof Error ? error.message : "Stream error occurred";
      onError?.(errorMsg);
      throw error;
    }
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
