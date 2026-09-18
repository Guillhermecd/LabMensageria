import { createContext, useContext, useMemo, useState, type ReactNode } from 'react';
import { authStorage } from '../api/modules/api';
import type { User } from '../api/modules/types';

type AuthContextValue = {
  user: User | null;
  isAuthenticated: boolean;
  login: (token: string, user: User) => void;
  logout: () => void;
};

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      isAuthenticated: !!authStorage.getToken(),
      login: (token, loggedUser) => {
        authStorage.setToken(token);
        setUser(loggedUser);
      },
      logout: () => {
        authStorage.clear();
        setUser(null);
      },
    }),
    [user],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
