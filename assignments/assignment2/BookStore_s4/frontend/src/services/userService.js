import api from './api';

const userService = {
  login: (data) => api.post('/auth/login', data),
  register: (data) => api.post('/auth/register', data),
  getCurrentUser: () => api.get('/user/me'),
  logout: () => api.post('/auth/logout'),
};

export default userService;
