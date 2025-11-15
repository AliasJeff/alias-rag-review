"use client";

import { Message } from "@/types";
import { User, Bot, MessageCircle, Loader } from "lucide-react";
import ReactMarkdown from "react-markdown";
import rehypeHighlight from "rehype-highlight";
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
          <MessageCircle className={styles.emptyIcon} size={48} />
          <div className={styles.emptyText}>暂无消息，开始对话吧</div>
        </div>
      ) : (
        <div className={styles.messageList}>
          {messages.map((msg) => (
            <div
              key={msg.id}
              className={`${styles.message} ${styles[msg.role]}`}
            >
              {msg.role === "user" ? (
                <>
                  <div className={styles.content}>
                    <div className={styles.role}>你</div>
                    <div className={styles.text}>
                      <ReactMarkdown
                        rehypePlugins={[rehypeHighlight]}
                        components={{
                          p: ({ children }) => (
                            <p className={styles.paragraph}>{children}</p>
                          ),
                          code: (props: any) => {
                            const { inline, children } = props;
                            return inline ? (
                              <code className={styles.inlineCode}>
                                {children}
                              </code>
                            ) : (
                              <code className={styles.codeBlock}>
                                {children}
                              </code>
                            );
                          },
                          pre: ({ children }) => (
                            <pre className={styles.preBlock}>{children}</pre>
                          ),
                          ul: ({ children }) => (
                            <ul className={styles.list}>{children}</ul>
                          ),
                          ol: ({ children }) => (
                            <ol className={styles.orderedList}>{children}</ol>
                          ),
                          li: ({ children }) => (
                            <li className={styles.listItem}>{children}</li>
                          ),
                          blockquote: ({ children }) => (
                            <blockquote className={styles.blockquote}>
                              {children}
                            </blockquote>
                          ),
                        }}
                      >
                        {msg.content}
                      </ReactMarkdown>
                    </div>
                    <div className={styles.time}>
                      {new Date(msg.createdAt).toLocaleTimeString()}
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
                      {!msg?.content && (
                        <div className={styles.loading}>
                          <Loader className={styles.spinner} size={16} />
                          <span>正在思考...</span>
                        </div>
                      )}
                      <ReactMarkdown
                        rehypePlugins={[rehypeHighlight]}
                        components={{
                          p: ({ children }) => (
                            <p className={styles.paragraph}>{children}</p>
                          ),
                          code: (props: any) => {
                            const { inline, children } = props;
                            return inline ? (
                              <code className={styles.inlineCode}>
                                {children}
                              </code>
                            ) : (
                              <code className={styles.codeBlock}>
                                {children}
                              </code>
                            );
                          },
                          pre: ({ children }) => (
                            <pre className={styles.preBlock}>{children}</pre>
                          ),
                          ul: ({ children }) => (
                            <ul className={styles.list}>{children}</ul>
                          ),
                          ol: ({ children }) => (
                            <ol className={styles.orderedList}>{children}</ol>
                          ),
                          li: ({ children }) => (
                            <li className={styles.listItem}>{children}</li>
                          ),
                          blockquote: ({ children }) => (
                            <blockquote className={styles.blockquote}>
                              {children}
                            </blockquote>
                          ),
                        }}
                      >
                        {msg.content}
                      </ReactMarkdown>
                    </div>
                    <div className={styles.time}>
                      {new Date(msg.createdAt).toLocaleTimeString()}
                    </div>
                  </div>
                </>
              )}
            </div>
          ))}
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
    </div>
  );
};
