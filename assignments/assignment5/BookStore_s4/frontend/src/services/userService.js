import api from './api';
import { getAccessToken, setAccessToken, clearAccessToken } from './authToken';

const userService = {
  login: async (data) => {
    const result = await api.post('/auth/login', data, { anonymous: true });
    if (!result.accessToken) throw new Error('登录响应缺少访问凭证');
    setAccessToken(result.accessToken);
    return result;
  },
  register: (data) => api.post('/auth/register', data, { anonymous: true }),
  getCurrentUser: () => getAccessToken() ? api.get('/user/me') : Promise.resolve(null),
  logout: async () => {
    clearAccessToken();
    // Clearing the local credential completes logout even if the network is unavailable.
    try { await api.post('/auth/logout', undefined, { anonymous: true }); } catch { /* already signed out */ }
  },
};

export default userService;
