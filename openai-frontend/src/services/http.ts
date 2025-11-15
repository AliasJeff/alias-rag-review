import axios, { AxiosInstance, AxiosRequestConfig, AxiosResponse } from "axios";

/**
 * 创建HTTP客户端实例，包含全局请求拦截器
 */
export function createHttpClient(): AxiosInstance {
  const client = axios.create({
    baseURL: process.env.NEXT_PUBLIC_API_URL,
    timeout: 30000,
  });

  // 请求拦截器
  client.interceptors.request.use(
    (config) => {
      // 打印请求信息
      console.log("[HTTP Request]", {
        method: config.method?.toUpperCase(),
        url: config.url,
        baseURL: config.baseURL,
        headers: config.headers,
        params: config.params,
        data: config.data,
        timestamp: new Date().toISOString(),
      });

      return config;
    },
    (error) => {
      console.error("[HTTP Request Error]", error);
      return Promise.reject(error);
    }
  );

  // 响应拦截器
  client.interceptors.response.use(
    (response: AxiosResponse) => {
      console.log("[HTTP Response]", {
        status: response.status,
        statusText: response.statusText,
        url: response.config.url,
        data: response.data,
        timestamp: new Date().toISOString(),
      });

      return response;
    },
    (error) => {
      console.error("[HTTP Response Error]", {
        status: error.response?.status,
        statusText: error.response?.statusText,
        url: error.config?.url,
        data: error.response?.data,
        message: error.message,
        timestamp: new Date().toISOString(),
      });

      return Promise.reject(error);
    }
  );

  return client;
}

export const httpClient = createHttpClient();
