"use client";

import { useState, useEffect } from "react";
import { X, Save, Eye, EyeOff } from "lucide-react";
import { Config } from "@/types";
import styles from "./ConfigDrawer.module.css";
import { clientUserApi } from "@/services/api";
import { getOrCreateClientIdentifier } from "@/utils/clientIdentifier";

interface ConfigDrawerProps {
  isOpen: boolean;
  onClose: () => void;
  clientUser?: any;
}

interface ExtendedConfig extends Config {
  githubToken?: string;
  openaiApiKey?: string;
}

export const ConfigDrawer = ({
  isOpen,
  onClose,
  clientUser,
}: ConfigDrawerProps) => {
  const [config, setConfig] = useState<ExtendedConfig>({ model: "gpt-4o" });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [showGithubToken, setShowGithubToken] = useState(false);
  const [showOpenaiApiKey, setShowOpenaiApiKey] = useState(false);
  const [clientIdentifier, setClientIdentifier] = useState<string>("");

  useEffect(() => {
    if (isOpen) {
      const identifier = getOrCreateClientIdentifier();
      setClientIdentifier(identifier);
      if (clientUser) {
        // Use passed clientUser data directly
        setConfig({
          model: clientUser?.model || "gpt-4o",
          temperature: clientUser?.temperature || 0.7,
          maxTokens: clientUser?.maxTokens || 10000,
          githubToken: clientUser?.githubToken || "",
          openaiApiKey: clientUser?.openaiApiKey || "",
        });
      } else {
        // Fallback to fetching if no clientUser passed
        loadConfig(identifier);
      }
    }
  }, [isOpen, clientUser]);

  const loadConfig = async (identifier: string) => {
    setLoading(true);
    setError(null);
    try {
      // Load client user data (which includes tokens and config)
      const clientUser = await clientUserApi.getClientUserByIdentifier(
        identifier
      );

      setConfig({
        model: clientUser?.model || "gpt-4o",
        temperature: clientUser?.temperature || 0.7,
        maxTokens: clientUser?.maxTokens || 10000,
        githubToken: clientUser?.githubToken || "",
        openaiApiKey: clientUser?.openaiApiKey || "",
      });
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load config");
      // Set default config even if loading fails
      setConfig({ model: "gpt-4o" });
    } finally {
      setLoading(false);
    }
  };

  const handleSave = async () => {
    setLoading(true);
    setError(null);
    try {
      // Update GitHub token if provided
      if (config.githubToken) {
        await clientUserApi.updateGithubToken(
          clientIdentifier,
          config.githubToken
        );
      }

      // Update OpenAI API key if provided
      if (config.openaiApiKey) {
        await clientUserApi.updateOpenaiApiKey(
          clientIdentifier,
          config.openaiApiKey
        );
      }

      // Update model settings
      await clientUserApi.updateClientUser(clientIdentifier, {
        model: config.model,
        temperature: config.temperature,
        maxTokens: config.maxTokens,
      });

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
            <label className={styles.label}>GitHub Token</label>
            <div className={styles.passwordInputWrapper}>
              <input
                type={showGithubToken ? "text" : "password"}
                className={styles.input}
                value={config.githubToken || ""}
                onChange={(e) => handleChange("githubToken", e.target.value)}
                placeholder="输入 GitHub Token"
                disabled={loading}
              />
              <button
                type="button"
                className={styles.toggleButton}
                onClick={() => setShowGithubToken(!showGithubToken)}
                disabled={loading}
                title={showGithubToken ? "隐藏" : "显示"}
              >
                {showGithubToken ? <EyeOff size={18} /> : <Eye size={18} />}
              </button>
            </div>
          </div>

          <div className={styles.formGroup}>
            <label className={styles.label}>OpenAI API Key</label>
            <div className={styles.passwordInputWrapper}>
              <input
                type={showOpenaiApiKey ? "text" : "password"}
                className={styles.input}
                value={config.openaiApiKey || ""}
                onChange={(e) => handleChange("openaiApiKey", e.target.value)}
                placeholder="输入 OpenAI API Key"
                disabled={loading}
              />
              <button
                type="button"
                className={styles.toggleButton}
                onClick={() => setShowOpenaiApiKey(!showOpenaiApiKey)}
                disabled={loading}
                title={showOpenaiApiKey ? "隐藏" : "显示"}
              >
                {showOpenaiApiKey ? <EyeOff size={18} /> : <Eye size={18} />}
              </button>
            </div>
          </div>

          <div className={styles.formGroup}>
            <label className={styles.label}>模型</label>
            <select
              className={styles.input}
              value={config.model || ""}
              onChange={(e) => handleChange("model", e.target.value)}
              disabled={true}
            >
              <option value="">选择模型</option>
              <option value="gpt-4o">GPT-4o</option>
              <option value="gpt-4-turbo">GPT-4 Turbo</option>
              <option value="gpt-4">GPT-4</option>
              <option value="gpt-3.5-turbo">GPT-3.5 Turbo</option>
            </select>
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
              disabled={true}
            />
          </div>

          <div className={styles.formGroup}>
            <label className={styles.label}>Max Tokens</label>
            <input
              type="number"
              className={styles.input}
              value={config.maxTokens || 10000}
              onChange={(e) =>
                handleChange("maxTokens", parseInt(e.target.value))
              }
              min="1"
              disabled={true}
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
