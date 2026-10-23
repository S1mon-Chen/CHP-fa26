import React from 'react';
import { beforeEach, afterEach, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import api from '../services/api';
import { aiService } from '../services/aiService';
import { AuthProvider } from '../context/AuthContext';
import { useAuth } from '../context/useAuth';
import userService from '../services/userService';

vi.mock('../services/userService', () => ({ default: { getCurrentUser: vi.fn(), logout: vi.fn() } }));
const response = (body, status = 200, type = 'application/json') => new Response(
  status === 204 ? null : type === 'application/json' ? JSON.stringify(body) : body,
  { status, headers: { 'content-type': type } },
);
beforeEach(() => vi.stubGlobal('fetch', vi.fn()));
afterEach(() => vi.unstubAllGlobals());

it('sends credentials and a server CSRF token on mutations', async () => {
  fetch.mockResolvedValueOnce(response({ headerName: 'X-CSRF-TOKEN', token: 'token-1' }))
    .mockResolvedValueOnce(response({ order: { id: 123 } }));
  await api.post('/orders', { items: [] });
  expect(fetch).toHaveBeenNthCalledWith(1, '/api/auth/csrf', { credentials: 'include' });
  expect(fetch.mock.calls[1][1]).toMatchObject({ credentials: 'include', headers: { 'X-CSRF-TOKEN': 'token-1' } });
});

it('AI requests use the same session client without a caller-supplied identity', async () => {
  fetch.mockResolvedValueOnce(response({ headerName: 'X-CSRF-TOKEN', token: 'token-2' }))
    .mockResolvedValueOnce(response({ answer: '库存 12 本', toolCalls: [] }));
  expect(await aiService.chat('查询 12345')).toEqual({ answer: '库存 12 本', toolCalls: [] });
  expect(fetch.mock.calls[1][0]).toBe('/api/ai/shopping/chat');
  expect(JSON.parse(fetch.mock.calls[1][1].body)).toEqual({ query: '查询 12345', history: [] });
  expect(fetch.mock.calls[1][1].credentials).toBe('include');
});

it('accepts logout responses with no JSON body and reports API errors', async () => {
  fetch.mockResolvedValueOnce(response({ headerName: 'X-CSRF-TOKEN', token: 't' }))
    .mockResolvedValueOnce(response(null, 204));
  expect(await api.post('/auth/logout')).toBeNull();
  fetch.mockResolvedValueOnce(response({ message: '请先登录' }, 401));
  await expect(api.get('/user/me')).rejects.toMatchObject({ message: '请先登录', status: 401 });
});

function SessionFixture() {
  const { currentUser, handleLogout } = useAuth();
  return <><span>{currentUser?.username ?? '未登录'}</span><button onClick={handleLogout}>退出</button></>;
}

it('restores the session on mount and logs out on the server', async () => {
  userService.getCurrentUser.mockResolvedValue({ id: 17, username: 'alice' });
  userService.logout.mockResolvedValue(null);
  render(<AuthProvider><SessionFixture /></AuthProvider>);
  expect(await screen.findByText('alice')).toBeTruthy();
  fireEvent.click(screen.getByText('退出'));
  await waitFor(() => expect(screen.getByText('未登录')).toBeTruthy());
  expect(userService.logout).toHaveBeenCalledTimes(1);
});
