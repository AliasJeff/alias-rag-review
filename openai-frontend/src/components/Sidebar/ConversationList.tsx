"use client";

import { useState } from "react";
import { Conversation } from "@/types";
import { Plus, Trash2, Edit2 } from "lucide-react";
import styles from "./ConversationList.module.css";

interface ConversationListProps {
  conversations: Conversation[];
  activeId: string | null;
  onSelect: (id: string) => void;
  onCreate: () => void;
  onDelete: (id: string) => void;
  onUpdate?: (id: string, data: Partial<Conversation>) => void;
  onStatusChange?: (
    id: string,
    status: "active" | "closed" | "archived" | "error"
  ) => void;
  loading?: boolean;
  onShowDetail?: () => void;
}

export const ConversationList = ({
  conversations,
  activeId,
  onSelect,
  onCreate,
  onDelete,
  onUpdate,
  onStatusChange,
  loading = false,
  onShowDetail,
}: ConversationListProps) => {
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editTitle, setEditTitle] = useState("");

  const handleEditStart = (conv: Conversation) => {
    setEditingId(conv.id);
    setEditTitle(conv.title || "");
  };

  const handleEditSave = (id: string) => {
    if (editTitle.trim() && onUpdate) {
      onUpdate(id, { title: editTitle });
    }
    setEditingId(null);
  };

  return (
    <div className={styles.container}>
      <div className={styles.header}>
        <h2 className={styles.title}>对话历史</h2>
        <button
          className={styles.newButton}
          onClick={onCreate}
          disabled={loading}
          title="新建对话"
        >
          <Plus size={18} />
        </button>
      </div>

      <div className={styles.list}>
        {!conversations || conversations.length === 0 ? (
          <div className={styles.empty}>暂无对话记录</div>
        ) : (
          conversations.map((conv) => (
            <div
              key={conv.id}
              className={`${styles.item} ${
                activeId === conv.id ? styles.active : ""
              }`}
              onClick={() => onSelect(conv.id)}
            >
              <div className={styles.itemContent}>
                {editingId === conv.id ? (
                  <input
                    type="text"
                    value={editTitle}
                    onChange={(e) => setEditTitle(e.target.value)}
                    onBlur={() => handleEditSave(conv.id)}
                    onKeyDown={(e) => {
                      if (e.key === "Enter") {
                        handleEditSave(conv.id);
                      }
                    }}
                    className={styles.editInput}
                    autoFocus
                  />
                ) : (
                  <>
                    <div className={styles.itemTitle}>
                      {conv.title || "无标题"}
                    </div>
                    {conv.status && (
                      <div
                        className={`${styles.itemStatus} ${
                          styles[conv.status]
                        }`}
                      >
                        {getStatusLabel(conv.status)}
                      </div>
                    )}
                  </>
                )}
                <div className={styles.itemTime}>
                  {new Date(conv.updatedAt).toLocaleDateString()}
                </div>
              </div>
              <div className={styles.itemActions}>
                {editingId !== conv.id && (
                  <>
                    <button
                      className={styles.editButton}
                      onClick={(e) => {
                        e.stopPropagation();
                        handleEditStart(conv);
                      }}
                      title="编辑对话"
                    >
                      <Edit2 size={16} />
                    </button>
                    <button
                      className={styles.deleteButton}
                      onClick={(e) => {
                        e.stopPropagation();
                        onDelete(conv.id);
                      }}
                      title="删除对话"
                    >
                      <Trash2 size={16} />
                    </button>
                  </>
                )}
              </div>
            </div>
          ))
        )}
      </div>
    </div>
  );
};

function getStatusLabel(status: string): string {
  const labels: Record<string, string> = {
    active: "活跃",
    closed: "已关闭",
    archived: "已归档",
    error: "错误",
  };
  return labels[status] || status;
}
