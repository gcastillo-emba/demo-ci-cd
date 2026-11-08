import { createContext, useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../api/client';
import type { components } from '../api/generated/schema';
import { clearToken, getToken, setToken, UNAUTHORIZED_EVENT } from '../api/token';

export type User = components['schemas']['User'];
export type Role = components['schemas']['Role'];

export const ME_QUERY_KEY = ['me'] as const;

export interface AuthState {
  /** Hay token en la sesión (aunque `/api/me` aún no haya respondido). */
  isAuthenticated: boolean;
  user: User | undefined;
  isLoadingUser: boolean;
  userError: unknown;
  login: (email: string, password: string) => Promise<User>;
  logout: () => void;
}

export const AuthContext = createContext<AuthState | null>(null);

async function fetchMe(): Promise<User> {
  const { data } = await api.GET('/api/me');
  if (!data) throw new Error('Respuesta vacía de /api/me');
  return data;
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [token, setTokenState] = useState<string | null>(() => getToken());

  const meQuery = useQuery({
    queryKey: ME_QUERY_KEY,
    queryFn: fetchMe,
    enabled: token !== null,
    staleTime: 5 * 60 * 1000,
    retry: false,
  });

  const logout = useCallback(() => {
    clearToken();
    setTokenState(null);
    queryClient.clear();
  }, [queryClient]);

  // Un 401 en cualquier llamada autenticada (token vencido o inválido) cierra la sesión.
  useEffect(() => {
    const onUnauthorized = () => logout();
    window.addEventListener(UNAUTHORIZED_EVENT, onUnauthorized);
    return () => window.removeEventListener(UNAUTHORIZED_EVENT, onUnauthorized);
  }, [logout]);

  const login = useCallback(
    async (email: string, password: string) => {
      const { data } = await api.POST('/api/auth/login', { body: { email, password } });
      if (!data) throw new Error('Respuesta vacía de /api/auth/login');
      setToken(data.accessToken);
      queryClient.setQueryData(ME_QUERY_KEY, data.user);
      setTokenState(data.accessToken);
      return data.user;
    },
    [queryClient],
  );

  const value = useMemo<AuthState>(
    () => ({
      isAuthenticated: token !== null,
      user: token !== null ? meQuery.data : undefined,
      isLoadingUser: token !== null && meQuery.isPending,
      userError: meQuery.error,
      login,
      logout,
    }),
    [token, meQuery.data, meQuery.isPending, meQuery.error, login, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
