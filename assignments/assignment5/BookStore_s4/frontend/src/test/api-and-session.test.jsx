import React from 'react';
import { beforeEach, afterEach, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, waitFor, act } from '@testing-library/react';
import api from '../services/api';
import { aiService } from '../services/aiService';
import { AuthProvider } from '../context/AuthContext';
import { useAuth } from '../context/useAuth';
import userService from '../services/userService';
import { getAccessToken, setAccessToken } from '../services/authToken';

const response = (body, status = 200) => new Response(status === 204 ? null : JSON.stringify(body),
  { status, headers: { 'content-type': 'application/json' } });
beforeEach(() => { sessionStorage.clear(); vi.stubGlobal('fetch', vi.fn()); });
afterEach(() => { sessionStorage.clear(); vi.unstubAllGlobals(); });

it('sends one Bearer request without cookies or CSRF bootstrap', async () => {
  setAccessToken('jwt-1');
  fetch.mockResolvedValueOnce(response({ order: { id: 123 } }));
  await api.post('/orders', { items: [] });
  expect(fetch).toHaveBeenCalledTimes(1);
  expect(fetch.mock.calls[0]).toEqual(['/api/orders', expect.objectContaining({
    credentials: 'omit', headers: expect.objectContaining({ Authorization: 'Bearer jwt-1' }),
  })]);
});

it('AI uses the same Bearer client without a caller-supplied identity', async () => {
  setAccessToken('jwt-ai');
  fetch.mockResolvedValueOnce(response({ answer: '库存 12 本', toolCalls: [] }));
  expect(await aiService.chat('查询 12345')).toEqual({ answer: '库存 12 本', toolCalls: [] });
  expect(fetch.mock.calls[0][0]).toBe('/api/ai/shopping/chat');
  expect(JSON.parse(fetch.mock.calls[0][1].body)).toEqual({ query: '查询 12345', history: [] });
  expect(fetch.mock.calls[0][1].headers.Authorization).toBe('Bearer jwt-ai');
});

it('login replaces an expired credential without sending it to the login endpoint', async () => {
  setAccessToken('expired');
  fetch.mockResolvedValueOnce(response({ user: { id: 1 }, accessToken: 'new-jwt' }));
  await userService.login({ username: 'student', password: 'password' });
  expect(getAccessToken()).toBe('new-jwt');
  expect(fetch.mock.calls[0][1].headers.Authorization).toBeUndefined();
});

it('401 clears the current token and notifies the UI', async () => {
  setAccessToken('expired');
  const listener = vi.fn();
  window.addEventListener('bookstore:unauthorized', listener);
  try {
    fetch.mockResolvedValueOnce(response({ message: '请重新登录' }, 401));
    await expect(api.get('/books')).rejects.toMatchObject({ status: 401 });
    expect(getAccessToken()).toBeNull();
    expect(listener).toHaveBeenCalledTimes(1);
  } finally { window.removeEventListener('bookstore:unauthorized', listener); }
});

it('a late 401 cannot log out a newer login', async () => {
  setAccessToken('old');
  let finish;
  fetch.mockImplementationOnce(() => new Promise(resolve => { finish = resolve; }));
  const pending = api.get('/books');
  setAccessToken('new');
  finish(response({ message: 'expired' }, 401));
  await expect(pending).rejects.toMatchObject({ status: 401 });
  expect(getAccessToken()).toBe('new');
});

it('logout discards the token even when the server is unreachable', async () => {
  setAccessToken('jwt');
  fetch.mockRejectedValueOnce(new Error('offline'));
  await userService.logout();
  expect(getAccessToken()).toBeNull();
  expect(fetch.mock.calls[0][1].headers.Authorization).toBeUndefined();
});

it('a guest does not issue an unauthenticated profile request', async () => {
  expect(await userService.getCurrentUser()).toBeNull();
  expect(fetch).not.toHaveBeenCalled();
});

function AuthFixture() {
  const { currentUser, handleLogout, isLoginModalOpen } = useAuth();
  return <><span>{currentUser?.username ?? '未登录'}</span>
    <span>{isLoginModalOpen ? '需要重新登录' : ''}</span><button onClick={handleLogout}>退出</button></>;
}

it('restores identity with the saved JWT and clears it on logout', async () => {
  setAccessToken('saved-jwt');
  fetch.mockResolvedValueOnce(response({ id: 17, username: 'alice' }))
    .mockResolvedValueOnce(response(null, 204));
  render(<AuthProvider><AuthFixture /></AuthProvider>);
  expect(await screen.findByText('alice')).toBeTruthy();
  expect(fetch.mock.calls[0][1].headers.Authorization).toBe('Bearer saved-jwt');
  fireEvent.click(screen.getByText('退出'));
  await waitFor(() => expect(screen.getByText('未登录')).toBeTruthy());
  expect(getAccessToken()).toBeNull();
});

it('an expired token signs out the UI and opens the login modal', async () => {
  setAccessToken('saved-jwt');
  fetch.mockResolvedValueOnce(response({ id: 17, username: 'alice' }));
  render(<AuthProvider><AuthFixture /></AuthProvider>);
  expect(await screen.findByText('alice')).toBeTruthy();
  fetch.mockResolvedValueOnce(response({ message: 'expired' }, 401));
  await act(async () => { await expect(api.get('/books')).rejects.toMatchObject({ status: 401 }); });
  expect(screen.getByText('未登录')).toBeTruthy();
  expect(screen.getByText('需要重新登录')).toBeTruthy();
});

it('inventory tools use the integrated tool endpoint and Bearer client', async () => {
  setAccessToken('jwt-tools');
  fetch.mockResolvedValueOnce(response({ answer: '库存 12 本', toolCalls: [] }));
  await aiService.toolChat('查询 12345');
  expect(fetch.mock.calls[0][0]).toBe('/api/ai/tool-chat');
  expect(JSON.parse(fetch.mock.calls[0][1].body)).toEqual({ query: '查询 12345', history: [] });
  expect(fetch.mock.calls[0][1].headers.Authorization).toBe('Bearer jwt-tools');
});
