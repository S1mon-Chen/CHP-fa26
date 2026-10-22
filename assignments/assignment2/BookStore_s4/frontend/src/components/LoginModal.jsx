import React from 'react';
import { Modal, Button, Input, message } from 'antd';
const { Password } = Input;
import { useAuth } from '../context/useAuth';

const LoginModal = () => {
  const [msg, contextHolder] = message.useMessage();
  
  const {
    isLoginModalOpen,
    setIsLoginModalOpen,
    loginForm,
    setLoginForm,
    handleLogin,
    loading
  } = useAuth();

  const handleSubmit = async () => {
    const result = await handleLogin();
    if (result.success) {
      msg.success(result.message);
    } else {
      msg.error(result.message);
    }
  };

  return (
    <>
    {contextHolder}
    <Modal
      title="用户登录"
      open={isLoginModalOpen}
      onCancel={() => setIsLoginModalOpen(false)}
      footer={[
        <Button key="cancel" onClick={() => setIsLoginModalOpen(false)}>
          取消
        </Button>,
        <Button key="submit" type="primary" loading={loading} onClick={handleSubmit}>
          登录
        </Button>
      ]}
      width={400}
    >
      <div className="login-form">
        <div style={{ marginBottom: '16px' }}>
          <label style={{ display: 'block', marginBottom: '8px' }}>用户名</label>
          <Input
            placeholder="请输入用户名"
            value={loginForm.username}
            onChange={(e) => setLoginForm({ ...loginForm, username: e.target.value })}
          />
        </div>
        <div style={{ marginBottom: '16px' }}>
          <label style={{ display: 'block', marginBottom: '8px' }}>密码</label>
          <Password
            placeholder="请输入密码"
            value={loginForm.password}
            onChange={(e) => setLoginForm({ ...loginForm, password: e.target.value })}
          />
        </div>
      </div>
    </Modal>
    </>
  );
};

export default LoginModal;
