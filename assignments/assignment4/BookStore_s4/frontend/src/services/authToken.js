const TOKEN_KEY = 'bookstore.accessToken';

// Tab-scoped storage preserves refreshes; the server never stores a login session.
export const getAccessToken = () => sessionStorage.getItem(TOKEN_KEY);
export const setAccessToken = token => sessionStorage.setItem(TOKEN_KEY, token);
export const clearAccessToken = () => sessionStorage.removeItem(TOKEN_KEY);
