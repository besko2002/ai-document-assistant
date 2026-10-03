import { createContext } from 'react';
import type { LoginRequest, RegisterRequest, User } from '../api/types';

export type AuthStatus = 'loading' | 'authenticated' | 'anonymous';

export interface AuthContextValue {
  status: AuthStatus;
  user: User | null;
  /** One-off notice shown on the login page, e.g. after a token expiry. */
  notice: string | null;
  login: (payload: LoginRequest) => Promise<void>;
  register: (payload: RegisterRequest) => Promise<void>;
  logout: () => void;
  clearNotice: () => void;
}

export const AuthContext = createContext<AuthContextValue | null>(null);
