import { getAccessToken, clearAccessToken } from './authToken';

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
  const { anonymous = false, ...fetchOptions } = options;
  const token = anonymous ? null : getAccessToken();
  const headers = { 'Content-Type': 'application/json', ...fetchOptions.headers };
  if (token) headers.Authorization = `Bearer ${token}`;
  const response = await fetch(`${API_BASE_URL}${url}`, {
    ...fetchOptions, credentials: 'omit', headers,
  });
  // A late 401 from an older request must not clear a more recent login.
  if (response.status === 401 && token && getAccessToken() === token) {
    clearAccessToken();
    window.dispatchEvent(new Event('bookstore:unauthorized'));
  }
  return readResponse(response);
};

export default {
  get: (url, options = {}) => request(url, { ...options, method: 'GET' }),
  post: (url, data, options = {}) => request(url, { ...options, method: 'POST', body: JSON.stringify(data) }),
  put: (url, data, options = {}) => request(url, { ...options, method: 'PUT', body: JSON.stringify(data) }),
  delete: (url, options = {}) => request(url, { ...options, method: 'DELETE' }),
};
