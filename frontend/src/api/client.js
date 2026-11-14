import axios from 'axios';

export const TOKEN_KEY = 'iitsm_token';

export function getToken() {
  try { return localStorage.getItem(TOKEN_KEY); } catch { return null; }
}
export function setToken(token) {
  try { localStorage.setItem(TOKEN_KEY, token); } catch { /* storage blocked: the session lasts until reload */ }
}
export function clearToken() {
  try { localStorage.removeItem(TOKEN_KEY); } catch { /* nothing to clear */ }
}

const api = axios.create({ baseURL: import.meta.env.VITE_API_URL || 'http://localhost:8080' });

api.interceptors.request.use((config) => {
  const token = getToken();
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

// A 401 means the token is missing, expired or invalid: forget it and go to the login page.
// A failed login is also a 401, but that one is shown on the login form instead.
api.interceptors.response.use(
  (response) => response,
  (error) => {
    const url = error.config?.url || '';
    if (error.response?.status === 401 && !url.includes('/api/auth/login')) {
      clearToken();
      if (window.location.pathname !== '/login') window.location.assign('/login');
    }
    return Promise.reject(error);
  },
);

/** A readable message for any failed request, using the backend's ApiError body when there is one. */
export function errorMessage(error) {
  const body = error?.response?.data;
  if (body?.fieldErrors && Object.keys(body.fieldErrors).length) {
    const fields = Object.entries(body.fieldErrors).map(([field, msg]) => `${field} ${msg}`).join('; ');
    return `${body.message}: ${fields}`;
  }
  if (body?.message) return body.message;
  if (error?.request && !error.response) return 'Cannot reach the server. Is the backend running on port 8080?';
  return error?.message || 'Something went wrong';
}

/** Builds query parameters, leaving out empty values so the backend ignores those filters. */
export function cleanParams(params) {
  return Object.fromEntries(Object.entries(params).filter(([, v]) => v !== '' && v !== null && v !== undefined));
}

export default api;
