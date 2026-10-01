import React, { createContext, useState, useContext, useCallback } from 'react';
import orderService from '../services/orderService';
import { useAuth } from './AuthContext';

const OrdersContext = createContext();

export const useOrders = () => {
  const context = useContext(OrdersContext);
  if (!context) {
    throw new Error('useOrders must be used within an OrdersProvider');
  }
  return context;
};

export const OrdersProvider = ({ children }) => {
  const [orders, setOrders] = useState([]);
  const [salesData, setSalesData] = useState([]);
  const [dashboardData, setDashboardData] = useState({
    salesByBook: [],
    salesByUser: []
  });
  const [loading, setLoading] = useState(false);
  const { currentUser } = useAuth();

  // 获取订单（根据用户权限）
  const fetchOrders = useCallback(async () => {
    try {
      setLoading(true);
      let data;

      // 检查用户权限
      if (currentUser) {
        // 如果不是管理员，只获取自己的订单
        if (!currentUser.role || currentUser.role !== 'ADMIN') {
          data = await orderService.getUserOrders(currentUser.id);
        } else {
          // 管理员获取所有订单
          data = await orderService.getAllOrders();
        }
      } else {
        // 未登录用户，返回空数组
        data = [];
      }

      setOrders(data);
    } catch (error) {
      console.error('获取订单数据失败:', error);
    } finally {
      setLoading(false);
    }
  }, [currentUser]);

  // 获取销量统计数据
  const fetchSalesData = useCallback(async () => {
    try {
      setLoading(true);
      const data = await orderService.getSalesStatistics();
      setSalesData(data);
    } catch (error) {
      console.error('获取销量统计数据失败:', error);
    } finally {
      setLoading(false);
    }
  }, []);

  // 获取管理员Dashboard数据
  const fetchDashboardData = useCallback(async () => {
    try {
      setLoading(true);
      // 从后端API获取真实数据
      const [salesByBook, salesByUser] = await Promise.all([
        orderService.getSalesStatistics(),
        orderService.getUserSalesStatistics()
      ]);

      // 转换数据格式，确保字段匹配
      const formattedSalesByUser = salesByUser.map(item => ({
        userId: item.userId,
        username: item.username,
        totalAmount: item.totalAmount
      }));

      setDashboardData({
        salesByBook: salesByBook,
        salesByUser: formattedSalesByUser
      });
    } catch (error) {
      console.error('获取Dashboard数据失败:', error);
    } finally {
      setLoading(false);
    }
  }, []);

  const value = {
    orders,
    salesData,
    dashboardData,
    loading,
    fetchOrders,
    fetchSalesData,
    fetchDashboardData
  };

  return <OrdersContext.Provider value={value}>{children}</OrdersContext.Provider>;
};