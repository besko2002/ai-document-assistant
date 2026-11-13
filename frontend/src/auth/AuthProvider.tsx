import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { api, setUnauthorizedHandler } from '../api/api';
import { ApiError } from '../api/ApiError';
import { readToken, writeToken } from '../api/token';
import type { AuthResponse, LoginRequest, RegisterRequest, User } from '../api/types';
import { SESSION_EXPIRED_NOTICE } from '../lib/messages';
import { AuthContext, type AuthContextValue, type AuthStatus } from './authContext';

export function AuthProvider({ children }: { children: ReactNode }): JSX.Element {
  const [token, setToken] = useState<string | null>(() => readToken());
  const [user, setUser] = useState<User | null>(null);
  const [status, setStatus] = useState<AuthStatus>(() =>
    readToken() === null ? 'anonymous' : 'loading',
  );
  const [notice, setNotice] = useState<string | null>(null);

  /** Token we already resolved to a user, so login() does not re-fetch /me. */
  const resolvedToken = useRef<string | null>(null);

  const clearSession = useCallback((nextNotice: string | null): void => {
    writeToken(null);
    resolvedToken.current = null;
    setToken(null);
    setUser(null);
    setStatus('anonymous');
    setNotice(nextNotice);
  }, []);

  // Any authenticated 401 means the one-hour token is gone: drop the session.
  useEffect(() => {
    setUnauthorizedHandler(() => {
      clearSession(SESSION_EXPIRED_NOTICE);
    });
    return () => {
      setUnauthorizedHandler(null);
    };
  }, [clearSession]);

  // On a cold start with a stored token, confirm who we are.
  useEffect(() => {
    if (token === null || resolvedToken.current === token) {
      return;
    }
    const controller = new AbortController();
    let cancelled = false;
    setStatus('loading');

    api
      .me(controller.signal)
      .then((me) => {
        if (!cancelled) {
          // Only now is the token resolved. Marking it earlier broke React StrictMode, which runs
          // this effect twice: the second run saw "already resolved" and the app never left loading.
          resolvedToken.current = token;
          setUser(me);
          setStatus('authenticated');
        }
      })
      .catch((cause: unknown) => {
        if (cancelled) {
          return;
        }
        if (cause instanceof ApiError && cause.status === 401) {
          // The 401 handler already cleared the session and set the notice.
          return;
        }
        clearSession(null);
      });

    return () => {
      cancelled = true;
      controller.abort();
    };
  }, [token, clearSession]);

  const applySession = useCallback((response: AuthResponse): void => {
    writeToken(response.accessToken);
    resolvedToken.current = response.accessToken;
    setToken(response.accessToken);
    setUser(response.user);
    setStatus('authenticated');
    setNotice(null);
  }, []);

  const login = useCallback(
    async (payload: LoginRequest): Promise<void> => {
      applySession(await api.login(payload));
    },
    [applySession],
  );

  const register = useCallback(
    async (payload: RegisterRequest): Promise<void> => {
      applySession(await api.register(payload));
    },
    [applySession],
  );

  const logout = useCallback((): void => {
    clearSession(null);
  }, [clearSession]);

  const clearNotice = useCallback((): void => {
    setNotice(null);
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({ status, user, notice, login, register, logout, clearNotice }),
    [status, user, notice, login, register, logout, clearNotice],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
