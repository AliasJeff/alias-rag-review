import axios, { AxiosInstance, AxiosRequestConfig, AxiosResponse } from "axios";

const isDev = process.env.NODE_ENV === "development";

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
    (error) => Promise.reject(error)
  );

  client.interceptors.response.use(
    (response) => {
      const { data } = response;
      if (data?.code !== "0000") {
        throw new Error(data?.message || data?.info);
      }
      return response;
    },
    (error) => Promise.reject(error)
  );

  return client;
}

export const httpClient = createHttpClient();
