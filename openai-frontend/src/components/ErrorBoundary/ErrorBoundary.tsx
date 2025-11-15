"use client";

import React, { ReactNode, ReactElement } from "react";
import { AlertCircle, RotateCcw } from "lucide-react";

interface Props {
  children: ReactNode;
  fallback?: (error: Error, retry: () => void) => ReactElement;
  onError?: (error: Error, errorInfo: React.ErrorInfo) => void;
  boundary?: string; // 用于区分不同的 Error Boundary
}

interface State {
  hasError: boolean;
  error: Error | null;
}

export class ErrorBoundary extends React.Component<Props, State> {
  constructor(props: Props) {
    super(props);
    this.state = { hasError: false, error: null };
  }

  static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error };
  }

  componentDidCatch(error: Error, errorInfo: React.ErrorInfo) {
    console.error(
      `[ErrorBoundary${this.props.boundary ? `: ${this.props.boundary}` : ""}]`,
      error,
      errorInfo
    );
    this.props.onError?.(error, errorInfo);
  }

  retry = () => {
    this.setState({ hasError: false, error: null });
  };

  render() {
    if (this.state.hasError && this.state.error) {
      return (
        this.props.fallback?.(this.state.error, this.retry) || (
          <DefaultErrorFallback error={this.state.error} onRetry={this.retry} />
        )
      );
    }

    return this.props.children;
  }
}

interface DefaultErrorFallbackProps {
  error: Error;
  onRetry: () => void;
}

function DefaultErrorFallback({ error, onRetry }: DefaultErrorFallbackProps) {
  return (
    <div className="flex items-center justify-center min-h-screen bg-gray-50 p-4">
      <div className="bg-white rounded-lg shadow-lg p-8 max-w-md w-full">
        <div className="flex items-center justify-center w-12 h-12 mx-auto bg-red-100 rounded-full mb-4">
          <AlertCircle className="w-6 h-6 text-red-600" />
        </div>
        <h1 className="text-xl font-semibold text-center text-gray-900 mb-2">
          出错了
        </h1>
        <p className="text-sm text-gray-600 text-center mb-4">
          应用程序遇到了一个错误。请尝试重新加载或联系支持。
        </p>
        <details className="mb-6">
          <summary className="text-xs text-gray-500 cursor-pointer hover:text-gray-700 mb-2">
            错误详情
          </summary>
          <pre className="bg-gray-100 p-3 rounded text-xs text-gray-700 overflow-auto max-h-32">
            {error.message}
            {error.stack && `\n\n${error.stack}`}
          </pre>
        </details>
        <button
          onClick={onRetry}
          className="w-full flex items-center justify-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors font-medium"
        >
          <RotateCcw className="w-4 h-4" />
          重试
        </button>
      </div>
    </div>
  );
}
