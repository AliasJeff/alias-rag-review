import axios, { AxiosInstance, AxiosRequestConfig, AxiosResponse } from "axios";

const isDev = process.env.NODE_ENV === "development";

/**
 * 创建 HTTP 客户端实例
 * - dev 模式浏览器发出请求
 * - prod 模式 Node.js 发出请求
 */
export function createHttpClient(): AxiosInstance {
  if (isDev && typeof window !== "undefined") {
    // 浏览器端 fetch 封装
    const fetchClient = axios.create({
      baseURL: process.env.NEXT_PUBLIC_API_URL,
      timeout: 30000,
    });

    fetchClient.interceptors.request.use((config) => {
      console.log("[DEV HTTP Request]", {
        method: config.method?.toUpperCase(),
        url: config.url,
        params: config.params,
        data: config.data,
        timestamp: new Date().toISOString(),
      });
      return config;
    });

    fetchClient.interceptors.response.use((response: AxiosResponse) => {
      console.log("[DEV HTTP Response]", {
        status: response.status,
        url: response.config.url,
        data: response.data,
        timestamp: new Date().toISOString(),
      });
      return response;
    });

    return fetchClient;
  } else {
    // 生产或 SSR 使用普通 axios
    const client = axios.create({
      baseURL: process.env.NEXT_PUBLIC_API_URL,
      timeout: 30000,
    });

    client.interceptors.request.use(
      (config) => {
        console.log("[HTTP Request]", {
          method: config.method?.toUpperCase(),
          url: config.url,
          params: config.params,
          data: config.data,
          timestamp: new Date().toISOString(),
        });
        return config;
      },
      (error) => Promise.reject(error)
    );

    client.interceptors.response.use(
      (response) => response,
      (error) => Promise.reject(error)
    );

    return client;
  }
}

export const httpClient = createHttpClient();
