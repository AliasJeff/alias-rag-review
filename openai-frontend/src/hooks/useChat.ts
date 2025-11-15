import { useState, useCallback, useRef } from "react";
import { ChatRequest, ChatResponse, Message } from "@/types";
import { chatApi } from "@/services/modules/chat";
import { messageApi } from "@/services/modules/message";

export interface UseChatOptions {
  conversationId?: string;
  userId: string;
  systemPrompt?: string;
}

export const useChat = (options: UseChatOptions) => {
  const { conversationId: initialConvId, userId, systemPrompt } = options;

  const [conversationId, setConversationId] = useState<string | undefined>(
    initialConvId
  );
  const [messages, setMessages] = useState<Message[]>([]);
  const [loading, setLoading] = useState(false);
  const [streaming, setStreaming] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const eventSourceRef = useRef<EventSource | null>(null);

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
  }, [conversationId]);

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
    [conversationId, userId, systemPrompt]
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

          // Close previous event source if exists
          if (eventSourceRef.current) {
            eventSourceRef.current.close();
          }

          const eventSource = chatApi.chatStream(request);
          eventSourceRef.current = eventSource;

          let fullContent = "";
          let newConversationId = conversationId;

          eventSource.addEventListener("message", (event) => {
            try {
              const data = JSON.parse(event.data);

              if (data.conversationId) {
                newConversationId = data.conversationId;
                if (!conversationId) {
                  setConversationId(data.conversationId);
                }
              }

              if (data.content) {
                fullContent += data.content;
                onChunk?.(data.content);
              }
            } catch (err) {
              console.error("Failed to parse message:", err);
            }
          });

          eventSource.addEventListener("error", (event) => {
            eventSource.close();
            eventSourceRef.current = null;
            setStreaming(false);

            const errorMsg =
              event instanceof Event && "data" in event
                ? (event as any).data
                : "Stream error occurred";
            setError(errorMsg);
            reject(new Error(errorMsg));
          });

          eventSource.addEventListener("done", () => {
            eventSource.close();
            eventSourceRef.current = null;
            setStreaming(false);

            // Add user message
            const userMessage: Message = {
              id: `user-${Date.now()}`,
              conversationId: newConversationId || conversationId || "",
              role: "user",
              content: message,
              createdAt: new Date().toISOString(),
            };

            // Add assistant message
            const assistantMessage: Message = {
              id: `assistant-${Date.now()}`,
              conversationId: newConversationId || conversationId || "",
              role: "assistant",
              content: fullContent,
              createdAt: new Date().toISOString(),
            };

            setMessages((prev) => [...prev, userMessage, assistantMessage]);
            resolve();
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
    [conversationId, userId, systemPrompt]
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
  }, [conversationId]);

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
  }, [conversationId]);

  /**
   * 停止流式响应
   */
  const stopStream = useCallback(() => {
    if (eventSourceRef.current) {
      eventSourceRef.current.close();
      eventSourceRef.current = null;
      setStreaming(false);
    }
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
