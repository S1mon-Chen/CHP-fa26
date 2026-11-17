import { getAccessToken, clearAccessToken } from './authToken';

const API_BASE = import.meta.env.VITE_API_BASE_URL || '/api';

// fetch streams support the Authorization header, unlike native EventSource.
export async function waitForOrder(queueId, onStatus = () => {}, { attempts = 5, retryMs = 1000 } = {}) {
  for (let attempt = 0; attempt < attempts; attempt++) {
    const token = getAccessToken();
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 125000);
    let reader;
    try {
      const response = await fetch(`${API_BASE}/orders/${encodeURIComponent(queueId)}/stream`, {
        headers: { Authorization: `Bearer ${token}`, Accept: 'text/event-stream' },
        credentials: 'omit', signal: controller.signal,
      });
      if (!response.ok) {
        if (response.status === 401 && getAccessToken() === token) {
          clearAccessToken(); window.dispatchEvent(new Event('bookstore:unauthorized'));
        }
        const error = new Error(`订单推送连接失败（${response.status}）`);
        error.status = response.status;
        throw error;
      }
      reader = response.body.getReader();
      const decoder = new TextDecoder();
      let buffer = '';
      while (true) {
        const { value, done } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });
        // Normalize only complete lines: CRLF may itself span network chunks.
        const normalized = buffer.replace(/\r\n/g, '\n');
        buffer = normalized;
        let boundary;
        while ((boundary = buffer.indexOf('\n\n')) >= 0) {
          const block = buffer.slice(0, boundary); buffer = buffer.slice(boundary + 2);
          const lines = block.split('\n');
          if (!lines.includes('event:order-status') && !lines.includes('event: order-status')) continue;
          const data = lines.filter(line => line.startsWith('data:'))
            .map(line => line.slice(5).trimStart()).join('\n');
          const status = JSON.parse(data);
          onStatus(status);
          if (['SUCCEEDED', 'FAILED'].includes(status.status)) return status;
        }
      }
    } catch (error) {
      if ([400, 401, 403, 404].includes(error.status)) throw error;
      if (attempt === attempts - 1) throw new Error(`推送连接中断；排队号 ${queueId} 已保留，请重新连接查看结果`);
    } finally {
      clearTimeout(timer);
      await reader?.cancel().catch(() => {});
      controller.abort();
    }
    if (attempt < attempts - 1) await new Promise(resolve => setTimeout(resolve, retryMs));
  }
  throw new Error(`尚未收到结果；排队号 ${queueId} 已保留，请重新连接查看结果`);
}
