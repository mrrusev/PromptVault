import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react';
import { useNavigate } from 'react-router-dom';
import type { UserDto } from '../api/types';
import { registerUnauthorizedHandler, setToken } from '../api/tokenStore';
import { ROUTES } from '../routes';

interface AuthContextValue {
  token: string | null;
  user: UserDto | null;
  isAuthenticated: boolean;
  login: (token: string, user: UserDto) => void;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  // Token + user live in memory only — never persisted to storage or cookies.
  const [token, setTokenState] = useState<string | null>(null);
  const [user, setUser] = useState<UserDto | null>(null);
  const navigate = useNavigate();

  const login = useCallback((nextToken: string, nextUser: UserDto) => {
    setToken(nextToken);
    setTokenState(nextToken);
    setUser(nextUser);
  }, []);

  const logout = useCallback(() => {
    setToken(null);
    setTokenState(null);
    setUser(null);
  }, []);

  // Keep a stable handler the client can call on a 401 without re-registering
  // every time `navigate`/`logout` identity changes.
  const handlerRef = useRef<() => void>(() => {});
  handlerRef.current = () => {
    logout();
    navigate(ROUTES.login, { replace: true });
  };

  useEffect(() => {
    registerUnauthorizedHandler(() => handlerRef.current());
    return () => registerUnauthorizedHandler(null);
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({ token, user, isAuthenticated: !!token, login, logout }),
    [token, user, login, logout],
  );

  return <AuthContext value={value}>{children}</AuthContext>;
}

// The hook is intentionally co-located with its provider (standard React
// Context pattern); the react-refresh rule doesn't apply to this shared module.
// eslint-disable-next-line react-refresh/only-export-components
export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (ctx === null) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return ctx;
}
