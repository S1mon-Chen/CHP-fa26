import React, { createContext, useState, useContext } from 'react';
import userService from '../services/userService';

const AuthContext = createContext();

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};

export const AuthProvider = ({ children }) => {
  const [currentUser, setCurrentUser] = useState(null);
  const [loading, setLoading] = useState(false);
  const [isLoginModalOpen, setIsLoginModalOpen] = useState(false);
  const [isRegisterModalOpen, setIsRegisterModalOpen] = useState(false);
  const [loginForm, setLoginForm] = useState({
    username: '',
    password: ''
  });
  const [registerForm, setRegisterForm] = useState({
    username: '',
    password: '',
    email: ''
  });

  // 处理登录
  const handleLogin = async () => {
    if (!loginForm.username || !loginForm.password) {
      return { success: false, message: '请填写用户名和密码' };
    }

    try {
      setLoading(true);
      const result = await userService.login(loginForm);
      setCurrentUser(result.user);
      setIsLoginModalOpen(false);
      return { success: true, message: '登录成功' };
    } catch (error) {
      return { success: false, message: error.message || '登录失败' };
    } finally {
      setLoading(false);
    }
  };

  // 处理注册
  const handleRegister = async () => {
    if (!registerForm.username || !registerForm.password || !registerForm.email) {
      return { success: false, message: '请填写所有注册信息' };
    }

    try {
      setLoading(true);
      await userService.register(registerForm);
      setIsRegisterModalOpen(false);
      setIsLoginModalOpen(true);
      return { success: true, message: '注册成功，请登录' };
    } catch (error) {
      return { success: false, message: error.message || '注册失败' };
    } finally {
      setLoading(false);
    }
  };

  // 处理退出登录
  const handleLogout = () => {
    setCurrentUser(null);
    return { success: true, message: '退出登录成功' };
  };

  // 重置表单
  const resetLoginForm = () => {
    setLoginForm({ username: '', password: '' });
  };

  const resetRegisterForm = () => {
    setRegisterForm({ username: '', password: '', email: '' });
  };

  const value = {
    currentUser,
    loading,
    isLoginModalOpen,
    isRegisterModalOpen,
    loginForm,
    registerForm,
    setIsLoginModalOpen,
    setIsRegisterModalOpen,
    setLoginForm,
    setRegisterForm,
    handleLogin,
    handleRegister,
    handleLogout,
    resetLoginForm,
    resetRegisterForm
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};