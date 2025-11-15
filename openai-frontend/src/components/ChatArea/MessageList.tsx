"use client";

import { Message } from "@/types";
import styles from "./MessageList.module.css";

interface MessageListProps {
  messages: Message[];
  loading?: boolean;
}

export const MessageList = ({
  messages,
  loading = false,
}: MessageListProps) => {
  return (
    <div className={styles.container}>
      {messages.length === 0 ? (
        <div className={styles.empty}>
          <div className={styles.emptyIcon}>💬</div>
          <div className={styles.emptyText}>暂无消息，开始对话吧</div>
        </div>
      ) : (
        <div className={styles.messageList}>
          {messages.map((msg) => (
            <div
              key={msg.id}
              className={`${styles.message} ${styles[msg.role]}`}
            >
              <div className={styles.avatar}>
                {msg.role === "user" ? "👤" : "🤖"}
              </div>
              <div className={styles.content}>
                <div className={styles.role}>
                  {msg.role === "user" ? "你" : "AI"}
                </div>
                <div className={styles.text}>{msg.content}</div>
                <div className={styles.time}>
                  {new Date(msg.createdAt).toLocaleTimeString()}
                </div>
              </div>
            </div>
          ))}
          {loading && (
            <div className={`${styles.message} ${styles.assistant}`}>
              <div className={styles.avatar}>🤖</div>
              <div className={styles.content}>
                <div className={styles.role}>AI</div>
                <div className={styles.loading}>
                  <span></span>
                  <span></span>
                  <span></span>
                </div>
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
};
