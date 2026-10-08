import { api } from '../lib/api'
import type { Piggy } from './types'

const idem = (key: string) => ({ 'Idempotency-Key': key })

export const piggyApi = {
  list: () => api<Piggy[]>('/piggies'),
  get: (id: string) => api<Piggy>(`/piggies/${id}`),
  create: (body: { accountId: string; name: string; goal?: string }) => api<Piggy>('/piggies', { method: 'POST', body }),
  update: (id: string, body: { name?: string; goal?: string; clearGoal?: boolean }) => api<Piggy>(`/piggies/${id}`, { method: 'PATCH', body }),
  save: (id: string, amount: string, key: string) => api<Piggy>(`/piggies/${id}/deposits`, { method: 'POST', body: { amount }, headers: idem(key) }),
  redeem: (id: string, amount: string, key: string) => api<Piggy>(`/piggies/${id}/withdrawals`, { method: 'POST', body: { amount }, headers: idem(key) }),
  /** Fecha o porquinho; o que houver dentro volta para a conta. */
  close: (id: string) => api<void>(`/piggies/${id}`, { method: 'DELETE' }),
}
