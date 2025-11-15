"use client";

import { Message } from "@/types";
import { MessageList } from "./MessageList";
import { InputBox } from "./InputBox";
import styles from "./ChatArea.module.css";

interface ChatAreaProps {
  messages: Message[];
  onSendMessage: (message: string) => void;
  loading?: boolean;
  disabled?: boolean;
  streaming?: boolean;
  onStopStream?: () => void;
  onClearContext?: () => void;
  error?: string | null;
}

export const ChatArea = ({
  messages,
  onSendMessage,
  loading = false,
  disabled = false,
  streaming = false,
  onStopStream,
  onClearContext,
  error,
}: ChatAreaProps) => {
  return (
    <div className={styles.container}>
      <div
        style={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          padding: "12px 16px",
          borderBottom: "1px solid #e0e0e0",
          backgroundColor: "#fafafa",
        }}
      >
        <div style={{ fontSize: "14px", color: "#666" }}>
          {streaming && (
            <span style={{ color: "#ff9800" }}>● 流式传输中...</span>
          )}
          {error && <span style={{ color: "#d32f2f" }}>● 错误: {error}</span>}
        </div>
        <div style={{ display: "flex", gap: "8px" }}>
          {streaming && onStopStream && (
            <button
              onClick={onStopStream}
              style={{
                padding: "6px 12px",
                backgroundColor: "#ff9800",
                color: "white",
                border: "none",
                borderRadius: "4px",
                cursor: "pointer",
                fontSize: "12px",
              }}
            >
              停止
            </button>
          )}
          {onClearContext && (
            <button
              onClick={onClearContext}
              style={{
                padding: "6px 12px",
                backgroundColor: "#f44336",
                color: "white",
                border: "none",
                borderRadius: "4px",
                cursor: "pointer",
                fontSize: "12px",
              }}
            >
              清空历史
            </button>
          )}
        </div>
      </div>
      <MessageList messages={messages} loading={loading} />
      <InputBox
        onSend={onSendMessage}
        loading={loading || streaming}
        disabled={disabled || streaming}
      />
    </div>
  );
};
