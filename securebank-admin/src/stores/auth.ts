import { create } from 'zustand'
import type { Claims } from '../lib/jwt'

interface AuthState {
  /** O access token vive só em memória: recarregar a página o descarta e o cookie HttpOnly o renova. */
  accessToken: string | null
  claims: Claims | null
  status: 'loading' | 'authenticated' | 'anonymous'
  /** O servidor recusou (MFA_REQUIRED): esta sessão não tem a verificação em duas etapas e só alcança a segurança da conta. */
  mfaRequired: boolean
  setSession: (token: string, claims: Claims) => void
  setMfaRequired: (value: boolean) => void
  clear: () => void
}

export const useAuth = create<AuthState>((set) => ({
  accessToken: null,
  claims: null,
  status: 'loading',
  mfaRequired: false,
  setSession: (accessToken, claims) => set({ accessToken, claims, status: 'authenticated', mfaRequired: false }),
  setMfaRequired: (mfaRequired) => set({ mfaRequired }),
  clear: () => set({ accessToken: null, claims: null, status: 'anonymous', mfaRequired: false }),
}))
