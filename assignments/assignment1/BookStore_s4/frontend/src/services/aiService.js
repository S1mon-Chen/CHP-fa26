import api from './api';

// AI助手服务
export const aiService = {
  // 发送聊天请求
  chat: async (query, userId) => {
    try {
      const response = await fetch('http://localhost:8080/api/ai/chat', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          query,
          userId,
        }),
      });

      if (!response.ok) {
        throw new Error('AI助手请求失败');
      }

      return await response.text();
    } catch (error) {
      console.error('AI助手请求失败:', error);
      throw error;
    }
  },

  // 健康检查
  health: async () => {
    try {
      const response = await api.get('/ai/health');
      return response;
    } catch (error) {
      console.error('AI助手健康检查失败:', error);
      throw error;
    }
  },
};