import { api } from '../lib/api'
import type { Money, Page } from './types'

export type PixKeyType = 'CPF' | 'EMAIL' | 'PHONE' | 'RANDOM'

/** Chave Pix. O VALOR vem do cadastro do cliente (o servidor define): a tela só escolhe o tipo. */
export interface PixKey {
  id: string
  accountId: string
  type: PixKeyType
  key: string
  createdAt: string
}

/** O que a confirmação mostra antes de enviar: só nome e CPF MASCARADOS. */
export interface PixLookup {
  type: PixKeyType
  key: string
  name: string
  document: string
  bank: string
  ownAccount: boolean
}

/** Um Pix enviado ou recebido; [direction] é do ponto de vista de quem consulta. */
export interface PixEntry {
  id: string
  endToEndId: string
  sourceAccountId: string
  amount: Money
  message: string | null
  key: string
  counterpartName: string
  direction: 'SENT' | 'RECEIVED'
  refundOfId: string | null
  refundedAmount: Money | null
  refundableAmount: Money | null
  createdAt: string
}

export type ChargeStatus = 'ACTIVE' | 'PAID' | 'CANCELED' | 'EXPIRED'

/** Cobrança criada por mim (QR dinâmico): valor fixo, validade, uso único; [location] vai dentro do QR. */
export interface PixCharge {
  txid: string
  accountId: string
  amount: Money
  description: string | null
  status: ChargeStatus
  expiresAt: string
  createdAt: string
  paidAt: string | null
  location: string
}

/** O que o PAGADOR vê ao ler o QR de uma cobrança: nome e CPF mascarados. */
export interface PixChargeView {
  txid: string
  amount: Money
  description: string | null
  status: ChargeStatus
  expiresAt: string
  receiverName: string
  receiverDocument: string
  own: boolean
  bank: string
}

export type ScheduleStatus = 'SCHEDULED' | 'EXECUTED' | 'FAILED' | 'CANCELED'

export interface PixSchedule {
  id: string
  sourceAccountId: string
  key: string
  destinationName: string
  amount: Money
  message: string | null
  scheduledFor: string
  status: ScheduleStatus
  failureReason: string | null
  executedPixId: string | null
  createdAt: string
}

const idem = (key: string) => ({ 'Idempotency-Key': key })

export const pixApi = {
  keys: () => api<PixKey[]>('/pix/keys'),
  registerKey: (accountId: string, type: PixKeyType) => api<PixKey>('/pix/keys', { method: 'POST', body: { accountId, type } }),
  deleteKey: (id: string) => api<void>(`/pix/keys/${id}`, { method: 'DELETE' }),
  lookup: (key: string) => api<PixLookup>(`/pix/keys/lookup?key=${encodeURIComponent(key)}`),

  send: (body: { sourceAccountId: string; key: string; amount: string; message?: string }, key: string) =>
    api<PixEntry>('/pix/transfers', { method: 'POST', body, headers: idem(key) }),
  history: (page: number, size = 10) => api<Page<PixEntry>>(`/pix/transfers?page=${page}&size=${size}`),
  refund: (id: string, amount: string | undefined, key: string) =>
    api<PixEntry>(`/pix/transfers/${id}/refund`, { method: 'POST', body: amount ? { amount } : {}, headers: idem(key) }),

  createCharge: (body: { accountId: string; amount: string; description?: string; expiresInMinutes: number }) =>
    api<PixCharge>('/pix/charges', { method: 'POST', body }),
  charges: (page = 0) => api<Page<PixCharge>>(`/pix/charges?page=${page}&size=20`),
  charge: (txid: string) => api<PixChargeView>(`/pix/charges/${txid}`),
  cancelCharge: (txid: string) => api<void>(`/pix/charges/${txid}`, { method: 'DELETE' }),
  payCharge: (txid: string, sourceAccountId: string, key: string) =>
    api<PixEntry>(`/pix/charges/${txid}/pay`, { method: 'POST', body: { sourceAccountId }, headers: idem(key) }),

  schedule: (body: { sourceAccountId: string; key: string; amount: string; message?: string; scheduledFor: string }, key: string) =>
    api<PixSchedule>('/pix/schedules', { method: 'POST', body, headers: idem(key) }),
  schedules: (page = 0) => api<Page<PixSchedule>>(`/pix/schedules?page=${page}&size=50`),
  cancelSchedule: (id: string) => api<void>(`/pix/schedules/${id}`, { method: 'DELETE' }),
}
