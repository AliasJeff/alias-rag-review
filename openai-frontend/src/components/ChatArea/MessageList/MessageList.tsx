"use client";

import { Message as MessageType } from "@/types";
import { MessageCircle, Loader, Bot } from "lucide-react";
import ScrollToBottom from "react-scroll-to-bottom";
import { Message } from "./Message";
import styles from "./MessageList.module.css";

interface MessageListProps {
  messages: MessageType[];
  loading?: boolean;
  streaming?: boolean;
}

/**
 * MessageList component for displaying chat messages
 * Automatically scrolls to the bottom and shows loading state
 */
export const MessageList = ({
  messages,
  loading = false,
  streaming = false,
}: MessageListProps) => {
  return (
    <ScrollToBottom className={styles.container}>
      {messages.length === 0 ? (
        <div className={styles.empty}>
          <MessageCircle className={styles.emptyIcon} size={48} />
          <div className={styles.emptyText}>暂无消息，开始对话吧</div>
        </div>
      ) : (
        <div className={styles.messageList}>
          {messages.map((msg, index) => {
            const isLatest = index === messages.length - 1;
            return (
              <Message
                key={msg.id}
                message={msg}
                streaming={streaming && isLatest}
              />
            );
          })}
          {loading && (
            <div className={`${styles.message} ${styles.assistant}`}>
              <div className={styles.avatar}>
                <Bot size={24} />
              </div>
              <div className={styles.content}>
                <div className={styles.role}>AI</div>
                <div className={styles.loading}>
                  <Loader className={styles.spinner} size={16} />
                  <span>正在思考...</span>
                </div>
              </div>
            </div>
          )}
        </div>
      )}
    </ScrollToBottom>
  );
};
