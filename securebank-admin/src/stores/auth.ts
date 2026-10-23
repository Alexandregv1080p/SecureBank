import { create } from 'zustand'
import type { Claims } from '../lib/jwt'

interface AuthState {
  /** O access token vive só em memória: recarregar a página o descarta e o cookie HttpOnly o renova. */
  accessToken: string | null
  claims: Claims | null
  status: 'loading' | 'authenticated' | 'anonymous'
  setSession: (token: string, claims: Claims) => void
  clear: () => void
}

export const useAuth = create<AuthState>((set) => ({
  accessToken: null,
  claims: null,
  status: 'loading',
  setSession: (accessToken, claims) => set({ accessToken, claims, status: 'authenticated' }),
  clear: () => set({ accessToken: null, claims: null, status: 'anonymous' }),
}))
