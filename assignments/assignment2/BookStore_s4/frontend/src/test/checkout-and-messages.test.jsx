import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, act, waitFor } from '@testing-library/react';
import LoginModal from '../components/LoginModal';
import RegisterModal from '../components/RegisterModal';
import CartModal from '../components/CartModal';
import SuccessModal from '../components/SuccessModal';
import { CartProvider } from '../context/CartContext';
import { useCart } from '../context/useCart';
import { useAuth } from '../context/useAuth';
import orderService from '../services/orderService';

vi.mock('../context/useAuth', () => ({ useAuth: vi.fn() }));
vi.mock('../services/orderService', () => ({ default: { submitOrder: vi.fn() } }));

beforeEach(() => {
  useAuth.mockReturnValue({ currentUser: { id: 17 }, setIsLoginModalOpen: vi.fn(), loading: false,
    isLoginModalOpen: true, isRegisterModalOpen: true,
    loginForm: { username: '', password: '' }, registerForm: { username: '', password: '', email: '' },
    setLoginForm: vi.fn(), setRegisterForm: vi.fn(), setIsRegisterModalOpen: vi.fn(),
    handleLogin: vi.fn().mockResolvedValue({ success: false, message: '用户名或密码错误' }),
    handleRegister: vi.fn().mockResolvedValue({ success: false, message: '邮箱已存在' }),
  });
});

describe('account feedback', () => {
  it('shows login errors instead of throwing', async () => {
    render(<LoginModal />);
    fireEvent.click(screen.getByRole('button', { name: /登\s*录/ }));
    expect(await screen.findByText('用户名或密码错误')).toBeTruthy();
  });
  it('shows registration errors instead of throwing', async () => {
    render(<RegisterModal />);
    fireEvent.click(screen.getByRole('button', { name: /注\s*册/ }));
    expect(await screen.findByText('邮箱已存在')).toBeTruthy();
  });
});

function CartFixture() {
  const { addToCart, setIsCartModalOpen } = useCart();
  return <><button onClick={() => { addToCart({ id: 1, title: 'Java', price: 20 }); setIsCartModalOpen(true); }}>添加</button><CartModal /><SuccessModal /></>;
}

describe('checkout', () => {
  it('sends only one order while a request is pending', async () => {
    let resolveOrder;
    orderService.submitOrder.mockImplementation(() => new Promise(resolve => { resolveOrder = resolve; }));
    render(<CartProvider><CartFixture /></CartProvider>);
    fireEvent.click(screen.getByText('添加'));
    const buy = screen.getByRole('button', { name: '一键购买' });
    act(() => { buy.click(); buy.click(); });
    expect(orderService.submitOrder).toHaveBeenCalledTimes(1);
    await act(async () => resolveOrder({ order: { id: 123 } }));
    expect(await screen.findByText('订单已提交，订单号：123，待支付。')).toBeTruthy();
  });
  it('shows a failed order and allows retry without emptying the cart', async () => {
    orderService.submitOrder.mockRejectedValueOnce(new Error('库存不足')).mockResolvedValueOnce({});
    render(<CartProvider><CartFixture /></CartProvider>);
    fireEvent.click(screen.getByText('添加'));
    fireEvent.click(screen.getByRole('button', { name: /一键购买/ }));
    expect(await screen.findByText('库存不足')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: /一键购买/ }));
    await waitFor(() => expect(orderService.submitOrder).toHaveBeenCalledTimes(2));
  });
});
