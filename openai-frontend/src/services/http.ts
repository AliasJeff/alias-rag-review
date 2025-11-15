import axios, { AxiosInstance } from "axios";
import { showError } from "./notificationService";

/**
 * 创建 HTTP 客户端实例
 * - dev 模式浏览器发出请求
 * - prod 模式 Node.js 发出请求
 */
export function createHttpClient(): AxiosInstance {
  const client = axios.create({
    baseURL: process.env.NEXT_PUBLIC_API_URL,
    timeout: 30000,
  });

  client.interceptors.request.use(
    (config) => {
      return config;
    },
    (error) => {
      const message = error?.message || "Request failed";
      showError(message);
      return Promise.reject(error);
    }
  );

  client.interceptors.response.use(
    (response) => {
      const { data } = response;
      if (data?.code !== "0000") {
        showError(data?.message || data?.info || "Request failed");
        return Promise.reject(
          new Error(data?.message || data?.info || "Request failed")
        );
      }
      return response;
    },
    (error) => {
      const message =
        error?.response?.data?.message || error?.message || "An error occurred";
      showError(message);
      return Promise.reject(error);
    }
  );

  return client;
}

let httpClientInstance: AxiosInstance | null = null;

export function getHttpClient(): AxiosInstance {
  if (!httpClientInstance) {
    httpClientInstance = createHttpClient();
  }
  return httpClientInstance;
}

// For backward compatibility
export const httpClient = new Proxy({} as AxiosInstance, {
  get: (target, prop) => {
    return getHttpClient()[prop as keyof AxiosInstance];
  },
});
