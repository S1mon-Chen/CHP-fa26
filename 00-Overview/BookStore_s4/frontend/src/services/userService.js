import api from './api';

const userService = {
  // 用户登录
  login: async (loginData) => {
    return await api.post('/auth/login', loginData);
  },

  // 用户注册
  register: async (registerData) => {
    return await api.post('/auth/register', registerData);
  },

  // 获取用户信息
  getUserInfo: async (userId) => {
    return await api.get(`/users/${userId}`);
  },
};

export default userService;