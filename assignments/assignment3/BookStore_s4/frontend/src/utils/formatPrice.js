// 格式化价格为人民币格式
export const formatPrice = (price) => {
  if (typeof price !== 'number') {
    price = parseFloat(price) || 0;
  }
  return `¥${price.toFixed(2)}`;
};

// 计算购物车总金额
export const calculateCartTotal = (cart) => {
  if (!Array.isArray(cart)) return '0.00';
  return cart.reduce((total, item) => total + (item.price * item.quantity), 0).toFixed(2);
};