"use client";

import { useState } from "react";
import { Settings } from "lucide-react";
import { ConversationList } from "./ConversationList";
import { ConfigDrawer } from "../ConfigDrawer/ConfigDrawer";
import { Conversation } from "@/types";
import styles from "./Sidebar.module.css";

interface SidebarProps {
  conversations: Conversation[];
  activeConversationId: string | null | undefined;
  onSelectConversation: (id: string) => void;
  onCreateConversation: () => void;
  onDeleteConversation: (id: string) => void;
  onUpdateConversation?: (id: string, data: Partial<Conversation>) => void;
  onUpdateConversationStatus?: (
    id: string,
    status: "active" | "closed" | "archived" | "error"
  ) => void;
  loading?: boolean;
  clientUser?: any;
}

export const Sidebar = ({
  conversations,
  activeConversationId,
  onSelectConversation,
  onCreateConversation,
  onDeleteConversation,
  onUpdateConversation,
  onUpdateConversationStatus,
  loading = false,
  clientUser,
}: SidebarProps) => {
  const [showConfigDrawer, setShowConfigDrawer] = useState(false);
  const [showConversationDetail, setShowConversationDetail] = useState(false);

  return (
    <div className={styles.sidebar}>
      <ConversationList
        conversations={conversations}
        activeId={activeConversationId}
        onSelect={onSelectConversation}
        onCreate={onCreateConversation}
        onDelete={onDeleteConversation}
        onUpdate={onUpdateConversation}
        onStatusChange={onUpdateConversationStatus}
        loading={loading}
        onShowDetail={() => setShowConversationDetail(true)}
      />

      <div className={styles.footer}>
        <button
          className={styles.configButton}
          onClick={() => setShowConfigDrawer(true)}
          title="配置设置"
        >
          <Settings size={18} />
          <span>设置</span>
        </button>
      </div>

      <ConfigDrawer
        isOpen={showConfigDrawer}
        onClose={() => setShowConfigDrawer(false)}
        clientUser={clientUser}
      />
    </div>
  );
};
