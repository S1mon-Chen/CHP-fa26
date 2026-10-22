import api from './api';

const orderService = {
  // 提交订单
  submitOrder: async (orderData) => {
    return await api.post('/orders', orderData);
  },

  // 获取用户订单
  getUserOrders: async (userId) => {
    return await api.get(`/orders/user/${userId}`);
  },

  // 获取所有订单（管理员）
  getAllOrders: async () => {
    return await api.get('/orders/all');
  },

  // 获取销量统计
  getSalesStatistics: async () => {
    return await api.get('/orders/sales-statistics');
  },

  // 获取用户消费统计
  getUserSalesStatistics: async () => {
    return await api.get('/orders/user-sales-statistics');
  },
};

export default orderService;