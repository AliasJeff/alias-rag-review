import { useState, useCallback } from "react";

export interface Notification {
  id: string;
  type: "error" | "success" | "warning" | "info";
  message: string;
}

export const useNotification = () => {
  const [notifications, setNotifications] = useState<Notification[]>([]);

  const addNotification = useCallback(
    (
      message: string,
      type: "error" | "success" | "warning" | "info" = "error",
      duration: number = 5000
    ) => {
      const id = `${Date.now()}-${Math.random()}`;
      const notification: Notification = { id, type, message };

      setNotifications((prev) => [...prev, notification]);

      // Auto remove after duration
      if (duration > 0) {
        setTimeout(() => {
          setNotifications((prev) => prev.filter((n) => n.id !== id));
        }, duration);
      }

      return id;
    },
    []
  );

  const removeNotification = useCallback((id: string) => {
    setNotifications((prev) => prev.filter((n) => n.id !== id));
  }, []);

  return {
    notifications,
    addNotification,
    removeNotification,
  };
};
