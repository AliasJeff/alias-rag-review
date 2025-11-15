"use client";

import {Conversation} from "@/types";
import {Plus, Trash2} from "lucide-react";
import styles from "./ConversationList.module.css";

interface ConversationListProps {
    conversations: Conversation[];
    activeId: string | null;
    onSelect: (id: string) => void;
    onCreate: () => void;
    onDelete: (id: string) => void;
    loading?: boolean;
}

export const ConversationList = ({
                                     conversations,
                                     activeId,
                                     onSelect,
                                     onCreate,
                                     onDelete,
                                     loading = false,
                                 }: ConversationListProps) => {
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
                    <Plus size={18}/>
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
                                <div className={styles.itemTitle}>{conv.title}</div>
                                <div className={styles.itemTime}>
                                    {new Date(conv.updatedAt).toLocaleDateString()}
                                </div>
                            </div>
                            <button
                                className={styles.deleteButton}
                                onClick={(e) => {
                                    e.stopPropagation();
                                    onDelete(conv.id);
                                }}
                                title="删除对话"
                            >
                                <Trash2 size={16}/>
                            </button>
                        </div>
                    ))
                )}
            </div>
        </div>
    );
};
