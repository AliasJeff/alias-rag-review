"use client";

import React from "react";
import { ErrorBoundary } from "./ErrorBoundary";
import { AlertCircle, RotateCcw } from "lucide-react";

interface Props {
  children: React.ReactNode;
}

export function ChatAreaErrorBoundary({ children }: Props) {
  return (
    <ErrorBoundary
      boundary="ChatArea"
      fallback={(error, retry) => (
        <div className="flex-1 flex flex-col items-center justify-center bg-white">
          <AlertCircle className="w-12 h-12 text-red-500 mb-4" />
          <h2 className="text-lg font-semibold text-gray-900 mb-2">
            聊天区域加载失败
          </h2>
          <p className="text-sm text-gray-600 text-center mb-6 max-w-sm">
            抱歉，聊天区域遇到了问题。请尝试重新加载。
          </p>
          <button
            onClick={retry}
            className="flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors font-medium"
          >
            <RotateCcw className="w-4 h-4" />
            重试
          </button>
        </div>
      )}
    >
      {children}
    </ErrorBoundary>
  );
}
