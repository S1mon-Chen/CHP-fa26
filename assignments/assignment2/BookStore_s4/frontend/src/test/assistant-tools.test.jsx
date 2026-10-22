import React from 'react';
import { beforeEach, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import AIAssistant from '../components/AIAssistant';
import { aiService } from '../services/aiService';

vi.mock('../services/aiService', () => ({ aiService: { chat: vi.fn() } }));
beforeEach(() => {
  vi.clearAllMocks();
  Element.prototype.scrollIntoView = vi.fn();
});
function openAndSend(query) {
  render(<AIAssistant />);
  fireEvent.click(screen.getByRole('button', { name: '打开智能书店助手' }));
  fireEvent.change(screen.getByRole('textbox'), { target: { value: query } });
  fireEvent.click(screen.getByRole('button', { name: /发\s*送/ }));
}
it('displays the model answer and expandable tool arguments/results', async () => {
  aiService.chat.mockResolvedValue({ answer: '库存 12 本，模拟竞品价格 45.50 元。', toolCalls: [
    { round: 1, id: 'c1', name: 'check_inventory', arguments: '{"isbn":"12345"}', result: { ok: true, stock: 12 } },
  ] });
  openAndSend('查 12345 库存和价格');
  expect(await screen.findByText('库存 12 本，模拟竞品价格 45.50 元。')).toBeTruthy();
  expect(screen.getByText('查看查询记录（1 次）')).toBeTruthy();
  expect(screen.getByText('{"isbn":"12345"}')).toBeTruthy();
  expect(aiService.chat).toHaveBeenCalledWith('查 12345 库存和价格', []);
  fireEvent.change(screen.getByRole('textbox'), { target: { value: '再查一下' } });
  fireEvent.click(screen.getByRole('button', { name: /发\s*送/ }));
  await waitFor(() => expect(aiService.chat).toHaveBeenCalledTimes(2));
  expect(aiService.chat.mock.calls[1][1]).toEqual([
    { role: 'user', content: '查 12345 库存和价格' },
    { role: 'assistant', content: '库存 12 本，模拟竞品价格 45.50 元。' },
  ]);
});
it('shows API failure and permits another request', async () => {
  aiService.chat.mockRejectedValueOnce(new Error('模型 API 请求超时')).mockResolvedValueOnce({ answer: '恢复了', toolCalls: [] });
  openAndSend('查库存');
  await screen.findAllByText('模型 API 请求超时');
  fireEvent.change(screen.getByRole('textbox'), { target: { value: '重试' } });
  fireEvent.click(screen.getByRole('button', { name: /发\s*送/ }));
  expect(await screen.findByText('恢复了')).toBeTruthy();
});
it('does not submit while composing Chinese input', () => {
  render(<AIAssistant />);
  fireEvent.click(screen.getByRole('button', { name: '打开智能书店助手' }));
  fireEvent.change(screen.getByRole('textbox'), { target: { value: '库存' } });
  fireEvent.keyDown(screen.getByRole('textbox'), { key: 'Enter', isComposing: true });
  expect(aiService.chat).not.toHaveBeenCalled();
});
