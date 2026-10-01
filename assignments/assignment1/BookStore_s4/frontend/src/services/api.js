// 基础API配置
const API_BASE_URL = 'http://localhost:8080/api';

// 基础请求方法
const request = async (url, options = {}) => {
  try {
    const response = await fetch(`${API_BASE_URL}${url}`, {
      ...options,
      headers: {
        'Content-Type': 'application/json',
        ...options.headers,
      },
    });

    if (!response.ok) {
      const error = await response.json();
      // 如果有具体的字段错误，合并显示
      if (error.errors) {
        const errorMessages = Object.values(error.errors).join('; ');
        throw new Error(errorMessages);
      }
      throw new Error(error.message || '请求失败');
    }

    return await response.json();
  } catch (error) {
    console.error('API请求失败:', error);
    throw error;
  }
};

export default {
  get: (url, options = {}) => request(url, { ...options, method: 'GET' }),
  post: (url, data, options = {}) => request(url, { ...options, method: 'POST', body: JSON.stringify(data) }),
  put: (url, data, options = {}) => request(url, { ...options, method: 'PUT', body: JSON.stringify(data) }),
  delete: (url, options = {}) => request(url, { ...options, method: 'DELETE' }),
};