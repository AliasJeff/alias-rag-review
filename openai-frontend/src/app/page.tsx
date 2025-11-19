"use client";

import { useState } from "react";
import { ChatArea, Sidebar } from "@/components";
import {
  ErrorBoundary,
  SidebarErrorBoundary,
  ChatAreaErrorBoundary,
} from "@/components/ErrorBoundary";
import { useConversations, useClientUser, useChat } from "@/hooks";
import styles from "./page.module.css";

export default function Home() {
  const [activeConversationId, setActiveConversationId] = useState<
    string | undefined
  >(undefined);
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
    messages,
    loading: messagesLoading,
    chatStream,
    streaming,
    error: chatError,
    stopStream,
    clearContext,
  } = useChat({
    conversationId: activeConversationId,
    userId: clientUser?.clientIdentifier,
  });

  const activeConversation = conversations.find(
    (conv) => conv.id === activeConversationId
  );

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
        setActiveConversationId(undefined);
      }
    } catch (err) {
      console.error("Failed to delete conversation:", err);
    }
  };

  const handleChat = async (content: string) => {
    if (!activeConversationId) {
      console.warn("No conversation selected");
      return;
    }
    try {
      await chatStream(content);
    } catch (err) {
      console.error("Failed to send message:", err);
    }
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
    <ErrorBoundary boundary="Home">
      <div className={styles.container}>
        <SidebarErrorBoundary>
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
        </SidebarErrorBoundary>
        <ChatAreaErrorBoundary>
          <ChatArea
            messages={messages}
            onSendMessage={handleChat}
            loading={messagesLoading}
            disabled={!activeConversationId}
            streaming={streaming}
            error={chatError}
            onStopStream={stopStream}
            onClearContext={clearContext}
            prUrl={activeConversation?.prUrl}
          />
        </ChatAreaErrorBoundary>
      </div>
    </ErrorBoundary>
  );
}
