/**
 * 使用 react-hot-toast 的全局通知服务
 */

import toast from "react-hot-toast";

export const showNotification = (
  message: string,
  type: "error" | "success" | "warning" | "info" = "error",
  duration: number = 5000
) => {
  switch (type) {
    case "error":
      toast.error(message, { duration });
      break;
    case "success":
      toast.success(message, { duration });
      break;
    case "warning":
      toast(message, { duration, icon: "⚠️" });
      break;
    case "info":
      toast(message, { duration, icon: "ℹ️" });
      break;
  }
};

export const showError = (message: string, duration?: number) => {
  showNotification(message, "error", duration);
};

export const showSuccess = (message: string, duration?: number) => {
  showNotification(message, "success", duration);
};

export const showWarning = (message: string, duration?: number) => {
  showNotification(message, "warning", duration);
};

export const showInfo = (message: string, duration?: number) => {
  showNotification(message, "info", duration);
};
