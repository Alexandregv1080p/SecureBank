import { api } from '../lib/api'
import type { SessionInfo } from './types'

export const securityApi = {
  mfaStatus: () => api<{ enabled: boolean }>('/security/mfa'),
  sessions: () => api<SessionInfo[]>('/security/sessions'),
  revokeSession: (id: string) => api<void>(`/security/sessions/${id}`, { method: 'DELETE' }),
  changePassword: (currentPassword: string, newPassword: string) =>
    api<void>('/security/password', { method: 'POST', body: { currentPassword, newPassword } }),
  setupMfa: () => api<{ secret: string; otpauthUri: string }>('/security/mfa', { method: 'POST' }),
  confirmMfa: (code: string) => api<void>('/security/mfa/confirm', { method: 'POST', body: { code } }),
  disableMfa: (code: string) => api<void>('/security/mfa', { method: 'DELETE', body: { code } }),
}
