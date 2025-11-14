import { useState, useEffect, useCallback } from "react";
import { Conversation } from "@/types";
import { apiService } from "@/services/api";

export const useConversations = () => {
  const [conversations, setConversations] = useState<Conversation[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetchConversations = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await apiService.getConversations();
      setConversations(data);
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "Failed to fetch conversations"
      );
    } finally {
      setLoading(false);
    }
  }, []);

  const createConversation = useCallback(async (title: string) => {
    try {
      const newConversation = await apiService.createConversation(title);
      setConversations((prev) => [newConversation, ...prev]);
      return newConversation;
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "Failed to create conversation"
      );
      throw err;
    }
  }, []);

  const deleteConversation = useCallback(async (id: string) => {
    try {
      await apiService.deleteConversation(id);
      setConversations((prev) => prev.filter((conv) => conv.id !== id));
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "Failed to delete conversation"
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
    deleteConversation,
  };
};
