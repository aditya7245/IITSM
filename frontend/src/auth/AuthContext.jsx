import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import api, { clearToken, getToken, setToken } from '../api/client';
import { groupOf } from '../lib/roles';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  const refreshUser = useCallback(async () => {
    const { data } = await api.get('/api/auth/me');
    setUser(data);
    return data;
  }, []);

  // On first load a saved token is checked against /me; an invalid one is dropped.
  useEffect(() => {
    if (!getToken()) { setLoading(false); return; }
    refreshUser().catch(() => clearToken()).finally(() => setLoading(false));
  }, [refreshUser]);

  const login = useCallback(async (email, password) => {
    const { data } = await api.post('/api/auth/login', { email, password });
    setToken(data.token);
    return refreshUser();
  }, [refreshUser]);

  const register = useCallback(async (form) => {
    await api.post('/api/auth/register', form);
    return login(form.email, form.password);
  }, [login]);

  const logout = useCallback(() => {
    clearToken();
    setUser(null);
  }, []);

  const value = useMemo(() => ({
    user, group: user ? groupOf(user.role) : null, loading, login, register, logout, refreshUser,
  }), [user, loading, login, register, logout, refreshUser]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  return useContext(AuthContext);
}
