import { AuthContext } from './useAuth';
import React, { useState, useEffect, useRef } from 'react';
import userService from '../services/userService';

export const AuthProvider = ({ children }) => {
  const [currentUser, setCurrentUser] = useState(null);
  const [loading, setLoading] = useState(false);
  const authVersion = useRef(0);
  const authInFlight = useRef(false);

  useEffect(() => {
    let active = true;
    const version = authVersion.current;
    userService.getCurrentUser().then(user => {
      if (active && authVersion.current === version) setCurrentUser(user);
    }).catch(() => { /* A missing session leaves the visitor signed out. */ });
    return () => { active = false; };
  }, []);
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
    if (authInFlight.current) return { success: false, message: '正在登录，请稍候' };
    if (!loginForm.username || !loginForm.password) {
      return { success: false, message: '请填写用户名和密码' };
    }

    authInFlight.current = true;
    authVersion.current += 1;
    try {
      setLoading(true);
      const result = await userService.login(loginForm);
      setCurrentUser(result.user);
      setLoginForm({ username: '', password: '' });
      setIsLoginModalOpen(false);
      return { success: true, message: '登录成功' };
    } catch (error) {
      return { success: false, message: error.message || '登录失败' };
    } finally {
      authInFlight.current = false;
      setLoading(false);
    }
  };

  // 处理注册
  const handleRegister = async () => {
    if (authInFlight.current) return { success: false, message: '正在提交，请稍候' };
    if (!registerForm.username || !registerForm.password || !registerForm.email) {
      return { success: false, message: '请填写所有注册信息' };
    }

    authInFlight.current = true;
    try {
      setLoading(true);
      await userService.register(registerForm);
      setRegisterForm({ username: '', password: '', email: '' });
      setIsRegisterModalOpen(false);
      setIsLoginModalOpen(true);
      return { success: true, message: '注册成功，请登录' };
    } catch (error) {
      return { success: false, message: error.message || '注册失败' };
    } finally {
      authInFlight.current = false;
      setLoading(false);
    }
  };

  // 处理退出登录
  const handleLogout = async () => {
    authVersion.current += 1;
    try {
      await userService.logout();
      setCurrentUser(null);
      return { success: true, message: '退出登录成功' };
    } catch (error) {
      return { success: false, message: error.message || '退出登录失败' };
    }
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
