"use client";

import { useState, useEffect } from "react";
import { useChat, useClientUser, useConversations } from "@/hooks";
import { ChatArea, Sidebar } from "@/components";
import styles from "../page.module.css";

export default function ChatPage() {
  const [activeConversationId, setActiveConversationId] = useState<
    string | null
  >(null);
  const [useStreamMode, setUseStreamMode] = useState(true);

  const {
    clientUser,
    loading: clientUserLoading,
    error: clientUserError,
  } = useClientUser();

  const {
    conversations,
    loading: conversationsLoading,
    createConversation,
    updateConversation,
    updateConversationStatus,
    deleteConversation,
  } = useConversations(clientUser?.clientIdentifier);

  const {
    conversationId,
    setConversationId,
    messages,
    loading,
    streaming,
    error,
    chat,
    chatStream,
    stopStream,
    clearContext,
  } = useChat({
    conversationId: activeConversationId || undefined,
    userId: clientUser?.id || "",
  });

  // Sync active conversation with chat hook
  useEffect(() => {
    if (activeConversationId) {
      setConversationId(activeConversationId);
    }
  }, [activeConversationId, setConversationId]);

  const handleCreateConversation = async () => {
    try {
      const newConv = await createConversation("新对话");
      setActiveConversationId(newConv.id);
    } catch (err) {
      console.error("Failed to create conversation:", err);
    }
  };

  const handleUpdateConversation = async (id: string, data: Partial<any>) => {
    try {
      await updateConversation(id, data);
    } catch (err) {
      console.error("Failed to update conversation:", err);
    }
  };

  const handleUpdateConversationStatus = async (
    id: string,
    status: "active" | "closed" | "archived" | "error"
  ) => {
    try {
      await updateConversationStatus(id, status);
    } catch (err) {
      console.error("Failed to update conversation status:", err);
    }
  };

  const handleDeleteConversation = async (id: string) => {
    try {
      await deleteConversation(id);
      if (activeConversationId === id) {
        setActiveConversationId(null);
      }
    } catch (err) {
      console.error("Failed to delete conversation:", err);
    }
  };

  const handleSendMessage = async (content: string) => {
    if (!activeConversationId && !conversationId) {
      console.warn("No conversation selected");
      return;
    }

    try {
      if (useStreamMode) {
        await chatStream(content);
      } else {
        await chat(content);
      }
    } catch (err) {
      console.error("Failed to send message:", err);
    }
  };

  const handleClearContext = async () => {
    if (confirm("确定要清空对话历史吗？")) {
      try {
        await clearContext();
      } catch (err) {
        console.error("Failed to clear context:", err);
      }
    }
  };

  const handleStopStream = () => {
    stopStream();
  };

  // Show loading state while initializing client user
  if (clientUserLoading) {
    return (
      <div className={styles.container}>
        <div
          style={{
            display: "flex",
            justifyContent: "center",
            alignItems: "center",
            height: "100vh",
            fontSize: "18px",
            color: "#666",
          }}
        >
          <div>初始化中...</div>
        </div>
      </div>
    );
  }

  // Show error state if client user initialization failed
  if (clientUserError) {
    return (
      <div className={styles.container}>
        <div
          style={{
            display: "flex",
            justifyContent: "center",
            alignItems: "center",
            height: "100vh",
            fontSize: "18px",
            color: "#d32f2f",
          }}
        >
          <div>初始化失败: {clientUserError}</div>
        </div>
      </div>
    );
  }

  return (
    <div className={styles.container}>
      <Sidebar
        conversations={conversations}
        activeConversationId={activeConversationId}
        onSelectConversation={setActiveConversationId}
        onCreateConversation={handleCreateConversation}
        onDeleteConversation={handleDeleteConversation}
        onUpdateConversation={handleUpdateConversation}
        onUpdateConversationStatus={handleUpdateConversationStatus}
        loading={conversationsLoading}
        clientUser={clientUser}
      />
      <ChatArea
        messages={messages}
        onSendMessage={handleSendMessage}
        loading={loading || streaming}
        disabled={!activeConversationId && !conversationId}
        streaming={streaming}
        onStopStream={handleStopStream}
        onClearContext={handleClearContext}
        error={error}
        useStreamMode={useStreamMode}
        onToggleStreamMode={setUseStreamMode}
      />
    </div>
  );
}
