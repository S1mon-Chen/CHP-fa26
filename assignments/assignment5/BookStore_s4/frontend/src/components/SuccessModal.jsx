import React from 'react';
import { Modal, Button } from 'antd';
import { CheckOutlined } from '@ant-design/icons';
import { useCart } from '../context/useCart';

const SuccessModal = () => {
  const { isSuccessModalOpen, setIsSuccessModalOpen, lastOrder } = useCart();

  return (
    <Modal
      title="订单提交成功"
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
        <p>订单已提交，订单号：{lastOrder?.id}，待支付。</p>
      </div>
    </Modal>
  );
};

export default SuccessModal;
