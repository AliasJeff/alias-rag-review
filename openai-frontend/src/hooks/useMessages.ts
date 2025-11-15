import { useState, useEffect, useCallback } from "react";
import { Message } from "@/types";
import { chatApi } from "@/services/modules/chat";

/**
 * Hook for managing messages within a conversation
 * Uses chat API to fetch conversation context and messages
 */
export const useMessages = (conversationId: string | null) => {
  const [messages, setMessages] = useState<Message[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  /**
   * Fetch messages from conversation context via chat API
   */
  const fetchMessages = useCallback(async () => {
    if (!conversationId) return;

    setLoading(true);
    setError(null);
    try {
      const context = await chatApi.getContext(conversationId);
      setMessages(context.messages || []);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to fetch messages");
    } finally {
      setLoading(false);
    }
  }, [conversationId]);

  useEffect(() => {
    fetchMessages();
  }, [fetchMessages]);

  return {
    messages,
    loading,
    error,
    fetchMessages,
  };
};
