import { api } from '../lib/api'
import type { Account, Customer, LimitUsage, Notification, Page, Payment, Transaction, Transfer } from './types'

const idem = (key: string) => ({ 'Idempotency-Key': key })

export const bankingApi = {
  me: () => api<Customer>('/customers/me'),
  accounts: () => api<Account[]>('/accounts'),
  account: (id: string) => api<Account>(`/accounts/${id}`),
  openAccount: (type: 'CHECKING' | 'SAVINGS') => api<Account>('/accounts', { method: 'POST', body: { type } }),
  limits: (id: string) => api<LimitUsage[]>(`/accounts/${id}/limits`),
  statement: (id: string, page: number, from?: string, to?: string) => {
    const q = new URLSearchParams({ page: String(page), size: '10' })
    if (from) q.set('from', from)
    if (to) q.set('to', to)
    return api<Page<Transaction>>(`/accounts/${id}/statement?${q}`)
  },
  deposit: (id: string, amount: string, key: string) =>
    api<Transaction>(`/accounts/${id}/deposits`, { method: 'POST', body: { amount }, headers: idem(key) }),
  withdraw: (id: string, amount: string, key: string) =>
    api<Transaction>(`/accounts/${id}/withdrawals`, { method: 'POST', body: { amount }, headers: idem(key) }),
  transfer: (
    body: { sourceAccountId: string; destinationBranch: string; destinationAccountNumber: string; amount: string; description?: string },
    key: string,
  ) => api<Transfer>('/transfers', { method: 'POST', body, headers: idem(key) }),
  transfers: (page = 0) => api<Page<Transfer>>(`/transfers?page=${page}&size=10`),
  pay: (body: { accountId: string; amount: string; barcode: string; description?: string }, key: string) =>
    api<Payment>('/payments', { method: 'POST', body, headers: idem(key) }),
  payments: (page = 0) => api<Page<Payment>>(`/payments?page=${page}&size=10`),
  notifications: (page = 0, size = 20) => api<Page<Notification>>(`/notifications?page=${page}&size=${size}`),
  markRead: (id: string) => api<void>(`/notifications/${id}/read`, { method: 'POST' }),
}
