import { api } from '../lib/api'
import { decodeClaims } from '../lib/jwt'
import { useAuth } from '../stores/auth'

interface TokenResponse {
  mfaRequired: boolean
  mfaToken?: string
  accessToken?: string
}

export type LoginOutcome = { kind: 'done' } | { kind: 'mfa'; mfaToken: string }

function accept(res: TokenResponse): LoginOutcome {
  if (res.mfaRequired && res.mfaToken) return { kind: 'mfa', mfaToken: res.mfaToken }
  const token = res.accessToken!
  useAuth.getState().setSession(token, decodeClaims(token))
  return { kind: 'done' }
}

export async function login(email: string, password: string): Promise<LoginOutcome> {
  return accept(await api<TokenResponse>('/auth/login', { method: 'POST', body: { email, password }, anonymous: true }))
}

export async function verifyMfa(mfaToken: string, code: string): Promise<LoginOutcome> {
  return accept(await api<TokenResponse>('/auth/mfa/verify', { method: 'POST', body: { mfaToken, code }, anonymous: true }))
}

export interface RegisterData {
  name: string
  document: string
  email: string
  phone: string
  password: string
}

export function register(data: RegisterData) {
  return api<{ userId: string; customerId: string }>('/auth/register', { method: 'POST', body: data, anonymous: true })
}

export async function logout() {
  try {
    await api<void>('/auth/logout', { method: 'POST' })
  } finally {
    useAuth.getState().clear()
  }
}
