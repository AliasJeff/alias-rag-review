import { useState, useEffect, useCallback } from "react";
import { Message } from "@/types";
import { chatApi } from "@/services/modules/chat";
import { messageApi } from "@/services/modules/message";
import { useClientUser } from "./useClientUser";

/**
 * Hook for managing messages within a conversation
 * Uses chat API to fetch conversation context and messages
 */
export const useMessages = (conversationId: string | null | undefined) => {
  const [messages, setMessages] = useState<Message[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const { clientUser } = useClientUser();

  /**
   * Fetch messages from conversation context via chat API
   */
  const fetchMessages = useCallback(async () => {
    if (!conversationId) return;

    setLoading(true);
    setError(null);
    try {
      const res = await messageApi.getMessages(conversationId);
      setMessages(res || []);
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
    setMessages,
    loading,
    error,
    fetchMessages,
  };
};
