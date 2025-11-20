"use client";

import { Message as MessageType } from "@/types";
import { User, Bot, Loader } from "lucide-react";
import ReactMarkdown from "react-markdown";
import rehypeHighlight from "rehype-highlight";
import remarkGfm from "remark-gfm";
import { CodeBlock } from "./CodeBlock";
import styles from "./Message.module.css";

interface MessageProps {
  message: MessageType;
  streaming?: boolean;
}

/**
 * Markdown components configuration for rendering markdown content
 * Handles code blocks, tables, lists, and other markdown elements
 */
const getMarkdownComponents = (): Record<string, any> => ({
  p: ({ children }: any) => <p className={styles.paragraph}>{children}</p>,
  code: (props: any) => {
    const { inline, children, className } = props;
    if (!className) {
      return <code className={styles.inlineCode}>{children}</code>;
    } else {
      return <CodeBlock className={className}>{children}</CodeBlock>;
    }
  },
  pre: ({ children }: any) => {
    return <>{children}</>;
  },
  ul: ({ children }: any) => <ul className={styles.list}>{children}</ul>,
  ol: ({ children }: any) => <ol className={styles.orderedList}>{children}</ol>,
  li: ({ children }: any) => <li className={styles.listItem}>{children}</li>,
  blockquote: ({ children }: any) => (
    <blockquote className={styles.blockquote}>{children}</blockquote>
  ),
  table: ({ children }: any) => (
    <div className={styles.tableWrapper}>
      <table className={styles.table}>{children}</table>
    </div>
  ),
  thead: ({ children }: any) => (
    <thead className={styles.tableHead}>{children}</thead>
  ),
  tbody: ({ children }: any) => (
    <tbody className={styles.tableBody}>{children}</tbody>
  ),
  tr: ({ children }: any) => <tr className={styles.tableRow}>{children}</tr>,
  th: ({ children }: any) => <th className={styles.tableHeader}>{children}</th>,
  td: ({ children }: any) => <td className={styles.tableCell}>{children}</td>,
});

/**
 * Message component for rendering individual chat messages
 * Handles both user and assistant messages with proper styling and markdown rendering
 */
export const Message = ({ message, streaming = false }: MessageProps) => {
  const isUser = message.role === "user";
  const markdownComponents = getMarkdownComponents();

  return (
    <div className={`${styles.message} ${styles[message.role]}`}>
      {isUser ? (
        <>
          <div className={styles.content}>
            <div className={styles.role}>User</div>
            <div className={styles.text}>
              <ReactMarkdown
                remarkPlugins={[remarkGfm]}
                rehypePlugins={[rehypeHighlight]}
                components={markdownComponents}
              >
                {message.content}
              </ReactMarkdown>
            </div>
            <div className={styles.time}>
              {new Date(message.createdAt).toLocaleTimeString()}
            </div>
          </div>
          <div className={styles.avatar}>
            <User size={24} />
          </div>
        </>
      ) : (
        <>
          <div className={styles.avatar}>
            <Bot size={24} />
          </div>
          <div className={styles.content}>
            <div className={styles.role}>AI</div>
            <div className={styles.text}>
              {!message?.content && (
                <div className={styles.loading}>
                  <Loader className={styles.spinner} size={16} />
                  <span>正在思考...</span>
                </div>
              )}
              <ReactMarkdown
                remarkPlugins={[remarkGfm]}
                rehypePlugins={[rehypeHighlight]}
                components={markdownComponents}
              >
                {message.content}
              </ReactMarkdown>
              {message?.content && streaming && (
                <div className={styles.loading}>
                  <Loader className={styles.spinner} size={16} />
                  <span>正在生成...</span>
                </div>
              )}
            </div>
            <div className={styles.time}>
              {new Date(message.createdAt).toLocaleTimeString()}
            </div>
          </div>
        </>
      )}
    </div>
  );
};
