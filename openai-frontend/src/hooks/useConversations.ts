import { useState, useEffect, useCallback } from "react";
import { Conversation } from "@/types";
import { conversationApi } from "@/services/api";

export const useConversations = (clientIdentifier?: string) => {
  const [conversations, setConversations] = useState<Conversation[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetchConversations = useCallback(async () => {
    if (!clientIdentifier) return;

    setLoading(true);
    setError(null);
    try {
      const data = await conversationApi.getClientConversations(
        clientIdentifier
      );
      setConversations(data);
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "Failed to fetch conversations"
      );
    } finally {
      setLoading(false);
    }
  }, [clientIdentifier]);

  const createConversation = useCallback(
    async (title?: string, prUrl?: string) => {
      if (!clientIdentifier) {
        throw new Error("Client identifier not available");
      }

      try {
        const newConversation = await conversationApi.createConversation({
          clientIdentifier,
          title: title || "新对话",
          prUrl,
        });
        setConversations((prev) => [newConversation, ...prev]);
        return newConversation;
      } catch (err) {
        setError(
          err instanceof Error ? err.message : "Failed to create conversation"
        );
        throw err;
      }
    },
    [clientIdentifier]
  );

  const updateConversation = useCallback(
    async (id: string, data: Partial<Conversation>) => {
      try {
        const updated = await conversationApi.updateConversation(id, data);
        setConversations((prev) =>
          prev.map((conv) => (conv.id === id ? updated : conv))
        );
        return updated;
      } catch (err) {
        setError(
          err instanceof Error ? err.message : "Failed to update conversation"
        );
        throw err;
      }
    },
    []
  );

  const updateConversationStatus = useCallback(
    async (id: string, status: "active" | "closed" | "archived" | "error") => {
      try {
        await conversationApi.updateConversationStatus(id, status);
        setConversations((prev) =>
          prev.map((conv) => (conv.id === id ? { ...conv, status } : conv))
        );
      } catch (err) {
        setError(
          err instanceof Error
            ? err.message
            : "Failed to update conversation status"
        );
        throw err;
      }
    },
    []
  );

  const deleteConversation = useCallback(async (id: string) => {
    try {
      await conversationApi.deleteConversation(id);
      setConversations((prev) => prev.filter((conv) => conv.id !== id));
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "Failed to delete conversation"
      );
      throw err;
    }
  }, []);

  const getConversationsByPrUrl = useCallback(async (prUrl: string) => {
    try {
      const data = await conversationApi.getConversationsByPrUrl(prUrl);
      return data;
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "Failed to fetch conversations"
      );
      throw err;
    }
  }, []);

  useEffect(() => {
    fetchConversations();
  }, [fetchConversations]);

  return {
    conversations,
    loading,
    error,
    fetchConversations,
    createConversation,
    updateConversation,
    updateConversationStatus,
    deleteConversation,
    getConversationsByPrUrl,
  };
};
