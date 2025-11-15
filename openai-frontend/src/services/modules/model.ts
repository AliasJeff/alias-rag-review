import { httpClient } from "../http";
import { ApiResponse, Model } from "@/types";

/**
 * 模型相关API
 */
export const modelApi = {
  /**
   * 获取所有可用模型
   */
  async getModels(): Promise<Model[]> {
    const response = await httpClient.get<ApiResponse<Model[]>>(
      "/api/v1/models"
    );
    return response.data.data;
  },

  /**
   * 获取模型详情
   */
  async getModelDetail(id: string): Promise<Model> {
    const response = await httpClient.get<ApiResponse<Model>>(
      `/api/v1/models/${id}`
    );
    return response.data.data;
  },

  /**
   * 创建模型配置
   */
  async createModel(data: Partial<Model>): Promise<Model> {
    const response = await httpClient.post<ApiResponse<Model>>(
      "/api/v1/models",
      data
    );
    return response.data.data;
  },

  /**
   * 更新模型配置
   */
  async updateModel(id: string, data: Partial<Model>): Promise<Model> {
    const response = await httpClient.put<ApiResponse<Model>>(
      `/api/v1/models/${id}`,
      data
    );
    return response.data.data;
  },

  /**
   * 删除模型
   */
  async deleteModel(id: string): Promise<void> {
    await httpClient.delete(`/api/v1/models/${id}`);
  },
};
