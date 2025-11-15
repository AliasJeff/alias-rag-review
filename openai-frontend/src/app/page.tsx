"use client";

import { useState } from "react";
import { Sidebar, ChatArea } from "@/components";
import { useConversations, useMessages } from "@/hooks";
import styles from "./page.module.css";

export default function Home() {
  const [activeConversationId, setActiveConversationId] = useState<
    string | null
  >(null);
  const {
    conversations,
    loading: conversationsLoading,
    createConversation,
    deleteConversation,
  } = useConversations();
  const {
    messages,
    loading: messagesLoading,
    sendMessage,
  } = useMessages(activeConversationId);

  const handleCreateConversation = async () => {
    try {
      const newConv = await createConversation("新对话");
      setActiveConversationId(newConv.id);
    } catch (err) {
      console.error("Failed to create conversation:", err);
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
    if (!activeConversationId) {
      console.warn("No conversation selected");
      return;
    }
    try {
      await sendMessage(content);
    } catch (err) {
      console.error("Failed to send message:", err);
    }
  };

  return (
    <div className={styles.container}>
      <Sidebar
        conversations={conversations}
        activeConversationId={activeConversationId}
        onSelectConversation={setActiveConversationId}
        onCreateConversation={handleCreateConversation}
        onDeleteConversation={handleDeleteConversation}
        loading={conversationsLoading}
      />
      <ChatArea
        messages={messages}
        onSendMessage={handleSendMessage}
        loading={messagesLoading}
        disabled={!activeConversationId}
      />
    </div>
  );
}
