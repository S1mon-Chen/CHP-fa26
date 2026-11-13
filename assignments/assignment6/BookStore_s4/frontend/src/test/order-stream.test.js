import { afterEach, expect, test, vi } from 'vitest';
import { waitForOrder } from '../services/orderStream';
import { setAccessToken, getAccessToken } from '../services/authToken';

const encode = value => new TextEncoder().encode(value);
function response(chunks) {
  return { ok: true, body: new ReadableStream({ start(controller) {
    chunks.forEach(chunk => controller.enqueue(encode(chunk))); controller.close();
  } }) };
}
afterEach(() => { vi.unstubAllGlobals(); sessionStorage.clear(); });

test('reads split CRLF frames with Bearer auth and stops on success', async () => {
  setAccessToken('token');
  const statuses = [];
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response([
    'event:order-status\r', '\ndata:{"status":"QUEUED"}\r\n\r', '\n',
    'event:order-status\ndata:{"status":"SUCCEEDED","orderId":8}\n\n',
  ])));
  const result = await waitForOrder('queue-id', value => statuses.push(value.status));
  expect(result.orderId).toBe(8); expect(statuses).toEqual(['QUEUED', 'SUCCEEDED']);
  expect(fetch).toHaveBeenCalledWith('/api/orders/queue-id/stream', expect.objectContaining({
    headers: { Authorization: 'Bearer token', Accept: 'text/event-stream' }, credentials: 'omit',
  }));
});

test('reconnects to the same queue when a connection ends before completion', async () => {
  vi.stubGlobal('fetch', vi.fn()
    .mockResolvedValueOnce(response([': heartbeat\n\n']))
    .mockResolvedValueOnce(response(['event:order-status\ndata:{"status":"FAILED","message":"库存不足"}\n\n'])));
  expect((await waitForOrder('same-id', () => {}, { retryMs: 0 })).status).toBe('FAILED');
  expect(fetch).toHaveBeenCalledTimes(2);
  expect(fetch.mock.calls.map(([url]) => url)).toEqual(['/api/orders/same-id/stream','/api/orders/same-id/stream']);
});

test('does not reconnect on 401 and clears only the rejected session', async () => {
  setAccessToken('expired');
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 401 }));
  await expect(waitForOrder('queue-id')).rejects.toThrow('401');
  expect(getAccessToken()).toBeNull(); expect(fetch).toHaveBeenCalledTimes(1);
});
