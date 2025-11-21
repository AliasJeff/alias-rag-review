import { useState, useCallback, useRef, useEffect } from "react";
import { ChatRequest, ChatResponse, Message } from "@/types";
import { chatApi } from "@/services/modules/chat";
import { useMessages } from "./useMessages";

export interface UseChatOptions {
  conversationId?: string;
  userId: string | undefined;
  systemPrompt?: string;
}

export const useChat = (options: UseChatOptions) => {
  const { conversationId: initialConvId, userId, systemPrompt } = options;

  const [conversationId, setConversationId] = useState<string | undefined>(
    initialConvId
  );
  const { messages, setMessages } = useMessages(conversationId);
  const [loading, setLoading] = useState(false);
  const [streaming, setStreaming] = useState(false);
  const [error, setError] = useState<string | null>(null);

  /**
   * 同步 conversationId 的变化
   */
  useEffect(() => {
    setConversationId(initialConvId);
  }, [initialConvId]);

  /**
   * 获取对话上下文
   */
  const getContext = useCallback(async () => {
    if (!conversationId) return null;

    try {
      setError(null);
      const context = await chatApi.getContext(conversationId);
      setMessages(context.messages);
      return context;
    } catch (err) {
      const errorMsg =
        err instanceof Error ? err.message : "Failed to get context";
      setError(errorMsg);
      throw err;
    }
  }, [conversationId, setMessages]);

  /**
   * 发送聊天消息（非流式）
   */
  const chat = useCallback(
    async (message: string) => {
      if (!userId) {
        throw new Error("User ID is required");
      }

      setLoading(true);
      setError(null);

      try {
        const request: ChatRequest = {
          message,
          userId,
          conversationId,
          systemPrompt,
        };

        const response = await chatApi.chat(request);

        // Update conversation ID if it was generated
        if (response.conversationId && !conversationId) {
          setConversationId(response.conversationId);
        }

        // Add user message to local state
        const userMessage: Message = {
          id: `user-${Date.now()}`,
          conversationId: response.conversationId,
          role: "user",
          content: message,
          createdAt: new Date().toISOString(),
        };

        // Add assistant message to local state
        const assistantMessage: Message = {
          id: `assistant-${Date.now()}`,
          conversationId: response.conversationId,
          role: "assistant",
          content: response.content,
          createdAt: new Date().toISOString(),
        };

        setMessages((prev) => [...prev, userMessage, assistantMessage]);

        return response;
      } catch (err) {
        const errorMsg =
          err instanceof Error ? err.message : "Failed to send message";
        setError(errorMsg);
        throw err;
      } finally {
        setLoading(false);
      }
    },
    [conversationId, userId, systemPrompt, setMessages]
  );

  /**
   * 发送聊天消息（流式）
   */
  const chatStream = useCallback(
    (message: string, onChunk?: (chunk: string) => void) => {
      return new Promise<void>((resolve, reject) => {
        if (!userId) {
          reject(new Error("User ID is required"));
          return;
        }

        setStreaming(true);
        setError(null);

        try {
          const request: ChatRequest = {
            message,
            userId,
            conversationId,
            systemPrompt,
          };

          let fullContent = "";
          let newConversationId = conversationId;
          const userMessageId = `user-${Date.now()}`;
          const assistantMessageId = `assistant-${Date.now()}`;

          // Add user message immediately
          const userMessage: Message = {
            id: userMessageId,
            conversationId: conversationId || "",
            role: "user",
            content: message,
            createdAt: new Date().toISOString(),
          };

          // Add empty assistant message immediately
          const assistantMessage: Message = {
            id: assistantMessageId,
            conversationId: conversationId || "",
            role: "assistant",
            content: "",
            createdAt: new Date().toISOString(),
          };

          setMessages((prev) => [...prev, userMessage, assistantMessage]);

          chatApi
            .chatStream(
              request,
              (chunk: string) => {
                let data: any = null;

                // 尝试解析 JSON
                try {
                  data = JSON.parse(chunk);
                } catch (_) {
                  // 不是 JSON，作为普通文本处理
                  const text = chunk.trim();
                  if (text.length > 0) {
                    fullContent += text;
                    onChunk?.(text);

                    setMessages((prev) =>
                      prev.map((msg) =>
                        msg.id === assistantMessageId
                          ? { ...msg, content: fullContent }
                          : msg
                      )
                    );
                  }
                  return; // 非 JSON 情况处理完，结束
                }

                // ===== JSON 情况 =====

                // 处理 conversationId
                if (data.conversationId) {
                  newConversationId = data.conversationId;
                  if (!conversationId) {
                    setConversationId(data.conversationId);
                    setMessages((prev) =>
                      prev.map((msg) =>
                        msg.id === userMessageId ||
                        msg.id === assistantMessageId
                          ? { ...msg, conversationId: data.conversationId }
                          : msg
                      )
                    );
                  }
                }

                // 处理 content 字段
                if (data.content) {
                  fullContent += data.content;
                  onChunk?.(data.content);

                  setMessages((prev) =>
                    prev.map((msg) =>
                      msg.id === assistantMessageId
                        ? { ...msg, content: fullContent }
                        : msg
                    )
                  );
                }
              },
              (error: string) => {
                // Handle error callback
                setStreaming(false);
                setError(error);
                reject(new Error(error));
              },
              () => {
                // Handle complete callback
                setStreaming(false);
                resolve();
              }
            )
            .catch((err) => {
              setStreaming(false);
              const errorMsg =
                err instanceof Error ? err.message : "Failed to start stream";
              setError(errorMsg);
              reject(err);
            });
        } catch (err) {
          setStreaming(false);
          const errorMsg =
            err instanceof Error ? err.message : "Failed to start stream";
          setError(errorMsg);
          reject(err);
        }
      });
    },
    [conversationId, userId, systemPrompt, setMessages]
  );

  /**
   * 清空对话历史
   */
  const clearContext = useCallback(async () => {
    if (!conversationId) return;

    try {
      setError(null);
      await chatApi.clearContext(conversationId);
      setMessages([]);
    } catch (err) {
      const errorMsg =
        err instanceof Error ? err.message : "Failed to clear context";
      setError(errorMsg);
      throw err;
    }
  }, [conversationId, setMessages]);

  /**
   * 删除对话
   */
  const deleteContext = useCallback(async () => {
    if (!conversationId) return;

    try {
      setError(null);
      await chatApi.deleteContext(conversationId);
      setMessages([]);
      setConversationId(undefined);
    } catch (err) {
      const errorMsg =
        err instanceof Error ? err.message : "Failed to delete context";
      setError(errorMsg);
      throw err;
    }
  }, [conversationId, setMessages]);

  /**
   * 停止流式响应
   */
  const stopStream = useCallback(() => {
    setStreaming(false);
  }, []);

  return {
    conversationId,
    setConversationId,
    messages,
    setMessages,
    loading,
    streaming,
    error,
    chat,
    chatStream,
    getContext,
    clearContext,
    deleteContext,
    stopStream,
  };
};
