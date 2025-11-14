"use client";

import { useState, useEffect } from "react";
import { X, Save } from "lucide-react";
import { Config } from "@/types";
import { apiService } from "@/services/api";
import styles from "./ConfigDrawer.module.css";

interface ConfigDrawerProps {
  isOpen: boolean;
  onClose: () => void;
}

export const ConfigDrawer = ({ isOpen, onClose }: ConfigDrawerProps) => {
  const [config, setConfig] = useState<Config>({});
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (isOpen) {
      loadConfig();
    }
  }, [isOpen]);

  const loadConfig = async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await apiService.getConfig();
      setConfig(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load config");
    } finally {
      setLoading(false);
    }
  };

  const handleSave = async () => {
    setLoading(true);
    setError(null);
    try {
      await apiService.saveConfig(config);
      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to save config");
    } finally {
      setLoading(false);
    }
  };

  const handleChange = (key: string, value: any) => {
    setConfig((prev) => ({
      ...prev,
      [key]: value,
    }));
  };

  if (!isOpen) return null;

  return (
    <div className={styles.overlay} onClick={onClose}>
      <div className={styles.drawer} onClick={(e) => e.stopPropagation()}>
        <div className={styles.header}>
          <h2 className={styles.title}>配置设置</h2>
          <button className={styles.closeButton} onClick={onClose} title="关闭">
            <X size={20} />
          </button>
        </div>

        <div className={styles.content}>
          {error && <div className={styles.error}>{error}</div>}

          <div className={styles.formGroup}>
            <label className={styles.label}>API URL</label>
            <input
              type="text"
              className={styles.input}
              value={config.apiUrl || ""}
              onChange={(e) => handleChange("apiUrl", e.target.value)}
              placeholder="http://localhost:3001/api"
              disabled={loading}
            />
          </div>

          <div className={styles.formGroup}>
            <label className={styles.label}>API Key</label>
            <input
              type="password"
              className={styles.input}
              value={config.apiKey || ""}
              onChange={(e) => handleChange("apiKey", e.target.value)}
              placeholder="输入 API Key"
              disabled={loading}
            />
          </div>

          <div className={styles.formGroup}>
            <label className={styles.label}>模型</label>
            <input
              type="text"
              className={styles.input}
              value={config.model || ""}
              onChange={(e) => handleChange("model", e.target.value)}
              placeholder="gpt-3.5-turbo"
              disabled={loading}
            />
          </div>

          <div className={styles.formGroup}>
            <label className={styles.label}>Temperature</label>
            <input
              type="number"
              className={styles.input}
              value={config.temperature || 0.7}
              onChange={(e) =>
                handleChange("temperature", parseFloat(e.target.value))
              }
              min="0"
              max="2"
              step="0.1"
              disabled={loading}
            />
          </div>

          <div className={styles.formGroup}>
            <label className={styles.label}>Max Tokens</label>
            <input
              type="number"
              className={styles.input}
              value={config.maxTokens || 2000}
              onChange={(e) =>
                handleChange("maxTokens", parseInt(e.target.value))
              }
              min="1"
              disabled={loading}
            />
          </div>
        </div>

        <div className={styles.footer}>
          <button
            className={styles.cancelButton}
            onClick={onClose}
            disabled={loading}
          >
            取消
          </button>
          <button
            className={styles.saveButton}
            onClick={handleSave}
            disabled={loading}
          >
            <Save size={16} />
            <span>保存</span>
          </button>
        </div>
      </div>
    </div>
  );
};
