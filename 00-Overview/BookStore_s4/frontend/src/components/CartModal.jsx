import React from 'react';
import { Modal, Button } from 'antd';
import { MinusOutlined, PlusOutlined } from '@ant-design/icons';
import { useCart } from '../context/CartContext';

const CartModal = () => {
  const {
    cart,
    isCartModalOpen,
    setIsCartModalOpen,
    removeFromCart,
    updateQuantity,
    calculateTotal,
    handleCheckout
  } = useCart();

  return (
    <Modal
      title="购物车"
      open={isCartModalOpen}
      onCancel={() => setIsCartModalOpen(false)}
      footer={[
        <Button key="close" onClick={() => setIsCartModalOpen(false)}>
          继续购物
        </Button>,
        <Button 
          key="checkout" 
          type="primary" 
          onClick={handleCheckout}
          disabled={cart.length === 0}
        >
          一键购买
        </Button>
      ]}
      width={600}
    >
      {cart.length === 0 ? (
        <div className="empty-cart">
          <p>购物车为空</p>
        </div>
      ) : (
        <div className="cart-content">
          {cart.map(item => (
            <div key={item.id} className="cart-item">
              <div className="cart-item-info">
                {item.coverImage ? (
                  item.coverImage.startsWith('http') ? (
                    <img 
                      src={item.coverImage} 
                      alt={item.title} 
                      className="cart-item-cover"
                    />
                  ) : (
                    <img 
                      src={`data:image/svg+xml;base64,${item.coverImage}`} 
                      alt={item.title} 
                      className="cart-item-cover"
                    />
                  )
                ) : (
                  <div className="cart-item-cover-placeholder">无封面</div>
                )}
                <div className="cart-item-details">
                  <h4>{item.title}</h4>
                  <p>{item.author}</p>
                  <p className="cart-item-price">¥{item.price.toFixed(2)}</p>
                </div>
              </div>
              <div className="cart-item-quantity">
                <Button 
                  icon={<MinusOutlined />} 
                  onClick={() => updateQuantity(item.id, item.quantity - 1)}
                />
                <span className="quantity">{item.quantity}</span>
                <Button 
                  icon={<PlusOutlined />} 
                  onClick={() => updateQuantity(item.id, item.quantity + 1)}
                />
                <Button 
                  danger 
                  onClick={() => removeFromCart(item.id)}
                >
                  删除
                </Button>
              </div>
            </div>
          ))}
          <div className="cart-total">
            <h3>总计: ¥{calculateTotal()}</h3>
          </div>
        </div>
      )}
    </Modal>
  );
};

export default CartModal;