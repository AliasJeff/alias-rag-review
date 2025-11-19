"use client";

import { Message } from "@/types";
import { MessageList } from "./MessageList";
import { InputBox } from "./InputBox";
import { ChatHeader } from "./Header";
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
  prUrl?: string;
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
  prUrl,
}: ChatAreaProps) => {
  return (
    <div className={styles.container}>
      <ChatHeader
        streaming={streaming}
        error={error}
        onStopStream={onStopStream}
        onClearContext={onClearContext}
        prUrl={prUrl}
      />
      <MessageList messages={messages} loading={loading} />
      <InputBox
        onSend={onSendMessage}
        loading={loading || streaming}
        disabled={disabled || streaming}
      />
    </div>
  );
};
