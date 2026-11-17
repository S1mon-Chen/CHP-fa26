import React from 'react';
import { beforeEach, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import AIAssistant from '../components/AIAssistant';
import { aiService } from '../services/aiService';

vi.mock('../services/aiService', () => ({ aiService: { chat: vi.fn(), toolChat: vi.fn() } }));
beforeEach(() => {
  vi.clearAllMocks();
  Element.prototype.scrollIntoView = vi.fn();
});
function openAndSend(query, mode = 'shopping') {
  render(<AIAssistant />);
  fireEvent.click(screen.getByRole('button', { name: '打开智能书店助手' }));
  fireEvent.change(screen.getByRole('combobox', { name: '咨询类型' }), { target: { value: mode } });
  fireEvent.change(screen.getByRole('textbox'), { target: { value: query } });
  fireEvent.click(screen.getByRole('button', { name: /发\s*送/ }));
}
it('displays the model answer and expandable tool arguments/results', async () => {
  aiService.chat.mockResolvedValue({ answer: '库存 12 本，模拟竞品价格 45.50 元。', steps: [
    { round: 1, id: 'c1', thought: '先查入门微服务书籍', action: { name: 'search_book_catalog', arguments: { query: '微服务' } }, observation: { ok: true, stock: 12 } },
  ] });
  openAndSend('查 12345 库存和价格');
  expect(await screen.findByText('库存 12 本，模拟竞品价格 45.50 元。')).toBeTruthy();
  expect(screen.getByText('查看 ReAct 执行记录（1 次）')).toBeTruthy();
  expect(screen.getByText('Thought：先查入门微服务书籍')).toBeTruthy();
  expect(screen.getByText('Action：search_book_catalog')).toBeTruthy();
  expect(screen.getByText('Observation')).toBeTruthy();
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

it('renders model Markdown tables without injecting raw HTML or remote images', async () => {
  aiService.chat.mockResolvedValue({ answer: '**教学模拟**\n\n| 书名 | 价格 |\n| --- | --- |\n| 微服务入门 | 69元 |\n\n<script>alert(1)</script>\n\n![追踪](https://example.com/tracker.png)', steps: [] });
  openAndSend('推荐微服务图书');
  expect(await screen.findByRole('table')).toBeTruthy();
  expect(screen.getByRole('cell', { name: '69元' })).toBeTruthy();
  expect(screen.queryByRole('img', { name: '追踪' })).toBeNull();
  expect(document.querySelector('.markdown-answer script')).toBeNull();
});

it('defaults to the inventory tools and displays failed calls followed by corrected results', async () => {
  aiService.toolChat.mockResolvedValue({ answer: '格式已纠正，库存 8 本。', toolCalls: [
    { round: 1, id: 'bad', name: 'check_inventory', arguments: '{"isbn":"978-7-111-42190-0"}',
      result: { ok: false, error: { code: 'INVALID_ISBN', suggested_isbn: '9787111421900' } } },
    { round: 2, id: 'good', name: 'check_inventory', arguments: '{"isbn":"9787111421900"}', result: { ok: true, stock: 8 } },
  ] });
  render(<AIAssistant />);
  fireEvent.click(screen.getByRole('button', { name: '打开智能书店助手' }));
  expect(screen.getByRole('combobox').value).toBe('tools');
  fireEvent.click(screen.getByRole('button', { name: 'ISBN 格式纠正示例' }));
  expect(screen.getByRole('textbox').value).toContain('978-7-111-42190-0');
  fireEvent.click(screen.getByRole('button', { name: /发\s*送/ }));
  expect(await screen.findByText('格式已纠正，库存 8 本。')).toBeTruthy();
  expect(aiService.chat).not.toHaveBeenCalled();
  expect(screen.getByText('查看查询记录（2 次）')).toBeTruthy();
  expect(screen.getByText(/INVALID_ISBN/)).toBeTruthy();
  expect(screen.getByText('第 2 轮 · check_inventory')).toBeTruthy();
  expect(screen.getByText('执行结果 · 失败')).toBeTruthy();
  expect(screen.getByText('执行结果 · 成功')).toBeTruthy();
});

it('keeps tool and shopping conversations separate and locks switching during requests', async () => {
  let resolveTool;
  aiService.toolChat.mockImplementationOnce(() => new Promise(resolve => { resolveTool = resolve; }))
    .mockResolvedValueOnce({ answer: '最新库存', toolCalls: [] });
  aiService.chat.mockResolvedValue({ answer: '退货政策', steps: [] });
  openAndSend('查询库存', 'tools');
  expect(screen.getByRole('combobox').disabled).toBe(true);
  resolveTool({ answer: '库存 12 本', toolCalls: [] });
  await screen.findByText('库存 12 本');
  fireEvent.change(screen.getByRole('combobox'), { target: { value: 'shopping' } });
  expect(screen.queryByText('库存 12 本')).toBeNull();
  fireEvent.change(screen.getByRole('textbox'), { target: { value: '能退吗' } });
  fireEvent.click(screen.getByRole('button', { name: /发\s*送/ }));
  await screen.findByText('退货政策');
  expect(aiService.chat).toHaveBeenCalledWith('能退吗', []);
  fireEvent.change(screen.getByRole('combobox'), { target: { value: 'tools' } });
  expect(screen.getByText('库存 12 本')).toBeTruthy();
  expect(screen.queryByText('退货政策')).toBeNull();
  fireEvent.change(screen.getByRole('textbox'), { target: { value: '再查一次' } });
  fireEvent.click(screen.getByRole('button', { name: /发\s*送/ }));
  await screen.findByText('最新库存');
  expect(aiService.toolChat).toHaveBeenLastCalledWith('再查一次', [
    { role: 'user', content: '查询库存' }, { role: 'assistant', content: '库存 12 本' },
  ]);
});
