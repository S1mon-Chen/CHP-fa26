import api from './api';

export const aiService = {
  chat: (query, history = []) => api.post('/ai/tool-chat', { query, history }),
  health: () => api.get('/ai/health'),
};
