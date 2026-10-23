const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api';

const readResponse = async (response) => {
  if (response.status === 204) return null;
  const isJson = response.headers.get('content-type')?.includes('application/json');
  const data = isJson ? await response.json() : await response.text();
  if (!response.ok) {
    const error = new Error(data?.errors ? Object.values(data.errors).join('; ') : data?.message || '请求失败');
    error.status = response.status;
    throw error;
  }
  return data;
};

const request = async (url, options = {}) => {
  const headers = { 'Content-Type': 'application/json', ...options.headers };
  if (options.method && !['GET', 'HEAD', 'OPTIONS'].includes(options.method)) {
    // A fresh token also handles the session/token rotation on login and logout.
    const token = await readResponse(await fetch(`${API_BASE_URL}/auth/csrf`, { credentials: 'include' }));
    headers[token.headerName] = token.token;
  }
  return readResponse(await fetch(`${API_BASE_URL}${url}`, {
    ...options, credentials: 'include', headers,
  }));
};

export default {
  get: (url, options = {}) => request(url, { ...options, method: 'GET' }),
  post: (url, data, options = {}) => request(url, { ...options, method: 'POST', body: JSON.stringify(data) }),
  put: (url, data, options = {}) => request(url, { ...options, method: 'PUT', body: JSON.stringify(data) }),
  delete: (url, options = {}) => request(url, { ...options, method: 'DELETE' }),
};
