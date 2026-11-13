import React from 'react';
import { Modal, Button } from 'antd';
import { CheckOutlined } from '@ant-design/icons';
import { useCart } from '../context/CartContext';

const SuccessModal = () => {
  const { isSuccessModalOpen, setIsSuccessModalOpen } = useCart();

  return (
    <Modal
      title="购买成功"
      open={isSuccessModalOpen}
      onCancel={() => setIsSuccessModalOpen(false)}
      footer={[
        <Button key="ok" type="primary" onClick={() => setIsSuccessModalOpen(false)}>
          确定
        </Button>
      ]}
      width={400}
    >
      <div className="success-content">
        <CheckOutlined className="success-icon" />
        <h3>订单已生成</h3>
        <p>您的购买已成功，订单号：{Math.floor(Math.random() * 1000000)}</p>
      </div>
    </Modal>
  );
};

export default SuccessModal;