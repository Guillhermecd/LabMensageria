import { api } from './api';
import type { User } from './types';

type AuthResponse = {
  token: string;
  user: User;
};

export const AuthService = {
  register(payload: { name: string; email: string; password: string }) {
    return api<AuthResponse>('/auth/register', {
      method: 'POST',
      auth: false,
      body: JSON.stringify(payload),
    });
  },
  login(payload: { email: string; password: string }) {
    return api<AuthResponse>('/auth/login', {
      method: 'POST',
      auth: false,
      body: JSON.stringify(payload),
    });
  },
};
