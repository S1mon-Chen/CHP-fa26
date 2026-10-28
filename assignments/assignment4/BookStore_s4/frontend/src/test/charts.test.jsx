import React from 'react';
import { it, expect, vi } from 'vitest';
import { render } from '@testing-library/react';
import * as echarts from 'echarts';
import StatisticsPage from '../pages/StatisticsPage';
import DashboardPage from '../pages/DashboardPage';

vi.mock('echarts', async (importOriginal) => ({ ...(await importOriginal()), init: vi.fn(() => ({ setOption: vi.fn(), resize: vi.fn(), dispose: vi.fn() })) }));
vi.mock('../context/useOrders', () => {
  const books = [{ bookId: 1, bookTitle: 'Java', bookAuthor: '作者', totalQuantity: 2, totalAmount: 40 }];
  const state = { salesData: books, dashboardData: { salesByBook: books, salesByUser: [{ userId: 1, username: '用户', totalAmount: 40 }] }, fetchSalesData: vi.fn(), fetchDashboardData: vi.fn() };
  return { useOrders: () => state };
});

it.each([StatisticsPage, DashboardPage])('cleans up every chart resize listener on unmount', (Page) => {
  const add = vi.spyOn(window, 'addEventListener');
  const remove = vi.spyOn(window, 'removeEventListener');
  const view = render(React.createElement(Page));
  const listeners = add.mock.calls.filter(([name]) => name === 'resize').map(([, listener]) => listener);
  const charts = echarts.init.mock.results.map(result => result.value);
  view.unmount();
  expect(listeners.length).toBeGreaterThan(0);
  for (const listener of listeners) expect(remove).toHaveBeenCalledWith('resize', listener);
  for (const chart of charts) expect(chart.dispose).toHaveBeenCalledTimes(1);
  add.mockRestore(); remove.mockRestore();
});
