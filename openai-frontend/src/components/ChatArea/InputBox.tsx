"use client";

import { useState } from "react";
import { Send } from "lucide-react";
import styles from "./InputBox.module.css";

interface InputBoxProps {
  onSend: (message: string) => void;
  loading?: boolean;
  disabled?: boolean;
}

export const InputBox = ({
  onSend,
  loading = false,
  disabled = false,
}: InputBoxProps) => {
  const [input, setInput] = useState("");

  const handleSend = () => {
    if (input.trim() && !loading && !disabled) {
      onSend(input);
      setInput("");
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  return (
    <div className={styles.container}>
      <div className={styles.inputWrapper}>
        <textarea
          className={styles.input}
          placeholder="输入消息... (Shift+Enter 换行)"
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={handleKeyDown}
          disabled={disabled}
          rows={3}
        />
        <button
          className={styles.sendButton}
          onClick={handleSend}
          disabled={!input.trim() || loading || disabled}
          title="发送消息"
        >
          <Send size={18} />
        </button>
      </div>
    </div>
  );
};
