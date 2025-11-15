"use client";

import React from "react";
import { ErrorBoundary } from "./ErrorBoundary";
import { AlertCircle, RotateCcw } from "lucide-react";

interface Props {
  children: React.ReactNode;
}

export function SidebarErrorBoundary({ children }: Props) {
  return (
    <ErrorBoundary
      boundary="Sidebar"
      fallback={(error, retry) => (
        <div className="w-64 bg-gray-50 border-r border-gray-200 p-4 flex flex-col items-center justify-center min-h-screen">
          <AlertCircle className="w-8 h-8 text-red-500 mb-2" />
          <p className="text-sm text-gray-600 text-center mb-4">
            侧边栏加载失败
          </p>
          <button
            onClick={retry}
            className="flex items-center gap-2 px-3 py-2 bg-blue-600 text-white rounded hover:bg-blue-700 transition-colors text-sm"
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
