import { api } from '../lib/api'
import type { Card, CardDetails, CardPurchase, Page } from './types'

const idem = (key: string) => ({ 'Idempotency-Key': key })

export const cardApi = {
  list: () => api<Card[]>('/cards'),
  get: (id: string) => api<Card>(`/cards/${id}`),
  issue: (accountId: string) => api<Card>('/cards', { method: 'POST', body: { accountId } }),
  /** Número completo, validade e CVV: cada consulta fica na auditoria do servidor. */
  reveal: (id: string) => api<CardDetails>(`/cards/${id}/reveal`, { method: 'POST' }),
  /** O motivo é obrigatório: fica no cartão e na auditoria. */
  block: (id: string, reason: string) => api<Card>(`/cards/${id}/block`, { method: 'POST', body: { reason } }),
  unblock: (id: string) => api<Card>(`/cards/${id}/unblock`, { method: 'POST' }),
  /** Definitivo: o cartão cancelado não volta. */
  cancel: (id: string, reason: string) => api<Card>(`/cards/${id}/cancel`, { method: 'POST', body: { reason } }),
  purchase: (id: string, body: { amount: string; merchant: string }, key: string) =>
    api<CardPurchase>(`/cards/${id}/purchases`, { method: 'POST', body, headers: idem(key) }),
  purchases: (id: string, page = 0, size = 20) => api<Page<CardPurchase>>(`/cards/${id}/purchases?page=${page}&size=${size}`),
}
