"use client";

import { useState } from "react";
import { Copy, Check } from "lucide-react";
import styles from "./CodeBlock.module.css";

interface CodeBlockProps {
  children: string;
  className?: string;
}

/**
 * CodeBlock component with copy functionality
 * Displays code with language highlighting and a copy button
 */
export const CodeBlock = ({ children, className }: CodeBlockProps) => {
  const [copied, setCopied] = useState(false);
  const code = String(children).replace(/\n$/, "");

  // Extract language from className (e.g., "language-python" -> "python")
  const language = className?.replace(/language-/, "").toUpperCase() || "CODE";

  const handleCopy = () => {
    navigator.clipboard.writeText(code);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className={styles.wrapper}>
      <div className={styles.header}>
        <span className={styles.language}>{language}</span>
        <button
          onClick={handleCopy}
          className={styles.copyButton}
          title="Copy code"
        >
          {copied ? (
            <>
              <Check size={14} className={styles.icon} />
              <span>Copied</span>
            </>
          ) : (
            <>
              <Copy size={14} className={styles.icon} />
              <span>Copy</span>
            </>
          )}
        </button>
      </div>
      <pre className={styles.pre}>
        <code className={className}>{children}</code>
      </pre>
    </div>
  );
};
