"use client";

import styles from "./ChatHeader.module.css";

interface ChatHeaderProps {
  streaming?: boolean;
  error?: string | null;
  onStopStream?: () => void;
  onClearContext?: () => void;
}

/**
 * ChatHeader component for displaying status and control buttons
 * Shows streaming status, errors, and provides action buttons
 */
export const ChatHeader = ({
  streaming = false,
  error,
  onStopStream,
  onClearContext,
}: ChatHeaderProps) => {
  return (
    <div className={styles.header}>
      <div className={styles.status}>
        {!streaming && !error && <span className={styles.ready}>● 就绪</span>}
        {streaming && <span className={styles.streaming}>● 传输中...</span>}
        {error && <span className={styles.error}>● 错误: {error}</span>}
      </div>
      <div className={styles.actions}>
        {streaming && onStopStream && (
          <button onClick={onStopStream} className={styles.stopButton}>
            停止
          </button>
        )}
        {onClearContext && (
          <button onClick={onClearContext} className={styles.clearButton}>
            清空历史
          </button>
        )}
      </div>
    </div>
  );
};
