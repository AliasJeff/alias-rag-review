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
}

export const ChatArea = ({
  messages,
  onSendMessage,
  loading = false,
  disabled = false,
}: ChatAreaProps) => {
  return (
    <div className={styles.container}>
      <MessageList messages={messages} loading={loading} />
      <InputBox onSend={onSendMessage} loading={loading} disabled={disabled} />
    </div>
  );
};
