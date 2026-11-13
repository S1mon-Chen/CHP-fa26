import { CartContext } from './useCart';
import React, { useState, useCallback, useRef } from 'react';
import orderService from '../services/orderService';
import { useAuth } from './useAuth';

export const CartProvider = ({ children }) => {
  const [cart, setCart] = useState([]);
  const [isCartModalOpen, setIsCartModalOpen] = useState(false);
  const [isSuccessModalOpen, setIsSuccessModalOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const [queueStatus, setQueueStatus] = useState(null);
  const pendingOrder = useRef(null);
  const [lastOrder, setLastOrder] = useState(null);
  const checkoutInFlight = useRef(false);
  const { currentUser, setIsLoginModalOpen } = useAuth();

  // 添加书籍到购物车
  const addToCart = useCallback((book) => {
    setCart(prevCart => {
      const existingBook = prevCart.find(item => item.id === book.id);
      if (existingBook) {
        return prevCart.map(item => 
          item.id === book.id 
            ? { ...item, quantity: item.quantity + 1 }
            : item
        );
      } else {
        return [...prevCart, { ...book, quantity: 1 }];
      }
    });
  }, []);

  // 从购物车移除书籍
  const removeFromCart = useCallback((bookId) => {
    setCart(prevCart => prevCart.filter(item => item.id !== bookId));
  }, []);

  // 更新书籍数量
  const updateQuantity = useCallback((bookId, newQuantity) => {
    if (!Number.isSafeInteger(newQuantity) || newQuantity < 1) return;
    setCart(prevCart => 
      prevCart.map(item => 
        item.id === bookId 
          ? { ...item, quantity: newQuantity }
          : item
      )
    );
  }, []);

  // 计算总金额
  const calculateTotal = useCallback(() => {
    return cart.reduce((total, item) => total + (item.price * item.quantity), 0).toFixed(2);
  }, [cart]);

  // 处理下单
  const handleCheckout = async () => {
    if (checkoutInFlight.current) return { success: false, pending: true };
    if (cart.length === 0) {
      return { success: false, message: '购物车为空' };
    }

    // 检查登录状态
    if (!currentUser) {
      setIsLoginModalOpen(true);
      return { success: false, message: '请先登录' };
    }

    checkoutInFlight.current = true;
    try {
      setLoading(true);
      // 构建订单数据
      const orderData = {
        items: cart.map(item => ({
          bookId: item.id,
          quantity: item.quantity
        }))
      };

      // 调用后端 API 提交订单
      const result = pendingOrder.current || await orderService.submitOrder(orderData);
      let completedOrder = result.order;
      if (result.queueId) {
        if (!pendingOrder.current) pendingOrder.current = { ...result, items: orderData.items };
        setQueueStatus(result);
        const status = await orderService.waitForOrder(result.queueId, setQueueStatus);
        if (status.status === 'FAILED') {
          pendingOrder.current = null;
          throw new Error(status.message);
        }
        completedOrder = { id: status.orderId };
      }
      const purchased = pendingOrder.current?.items || orderData.items;
      pendingOrder.current = null;
      setLastOrder(completedOrder);
      setIsSuccessModalOpen(true);
      // 清空购物车
      setCart(current => current.flatMap(item => {
        const bought = purchased.find(row => row.bookId === item.id)?.quantity || 0;
        return item.quantity > bought ? [{ ...item, quantity: item.quantity - bought }] : [];
      }));
      return { success: true, message: '订单提交成功' };
    } catch (error) {
      return { success: false, message: error.message || '订单提交失败' };
    } finally {
      checkoutInFlight.current = false;
      setLoading(false);
    }
  };

  // 清空购物车
  const clearCart = useCallback(() => {
    setCart([]);
  }, []);

  const value = {
    cart,
    isCartModalOpen,
    isSuccessModalOpen,
    loading,
    lastOrder,
    queueStatus,
    setIsCartModalOpen,
    setIsSuccessModalOpen,
    addToCart,
    removeFromCart,
    updateQuantity,
    calculateTotal,
    handleCheckout,
    clearCart
  };

  return <CartContext.Provider value={value}>{children}</CartContext.Provider>;
};
