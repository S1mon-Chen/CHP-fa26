import React from 'react';
import { Modal, Button, Input, message } from 'antd';
const { Password } = Input;
import { useAuth } from '../context/AuthContext';

const RegisterModal = () => {
  const { message: msg } = message.useMessage();
  
  const {
    isRegisterModalOpen,
    setIsRegisterModalOpen,
    registerForm,
    setRegisterForm,
    handleRegister
  } = useAuth();

  const handleSubmit = async () => {
    const result = await handleRegister();
    if (result.success) {
      msg.success(result.message);
    } else {
      msg.error(result.message);
    }
  };

  return (
    <Modal
      title="用户注册"
      open={isRegisterModalOpen}
      onCancel={() => setIsRegisterModalOpen(false)}
      footer={[
        <Button key="cancel" onClick={() => setIsRegisterModalOpen(false)}>
          取消
        </Button>,
        <Button key="submit" type="primary" onClick={handleSubmit}>
          注册
        </Button>
      ]}
      width={400}
    >
      <div className="register-form">
        <div style={{ marginBottom: '16px' }}>
          <label style={{ display: 'block', marginBottom: '8px' }}>用户名</label>
          <Input
            placeholder="请输入用户名"
            value={registerForm.username}
            onChange={(e) => setRegisterForm({ ...registerForm, username: e.target.value })}
          />
        </div>
        <div style={{ marginBottom: '16px' }}>
          <label style={{ display: 'block', marginBottom: '8px' }}>密码</label>
          <Password
            placeholder="请输入密码"
            value={registerForm.password}
            onChange={(e) => setRegisterForm({ ...registerForm, password: e.target.value })}
          />
        </div>
        <div style={{ marginBottom: '16px' }}>
          <label style={{ display: 'block', marginBottom: '8px' }}>邮箱</label>
          <Input
            placeholder="请输入邮箱"
            value={registerForm.email}
            onChange={(e) => setRegisterForm({ ...registerForm, email: e.target.value })}
          />
        </div>
      </div>
    </Modal>
  );
};

export default RegisterModal;