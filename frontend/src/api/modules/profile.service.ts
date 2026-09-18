import { api } from './api';
import type { User } from './types';

export const ProfileService = {
  getProfile() {
    return api<User>('/profile');
  },
  updateProfile(payload: { name: string }) {
    return api<User>('/profile', {
      method: 'PATCH',
      body: JSON.stringify(payload),
    });
  },
};
