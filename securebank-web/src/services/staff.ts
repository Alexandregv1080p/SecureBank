import { api } from '../lib/api'
import type { Money, Page } from './types'

export interface AuditLog {
  id: string
  occurredAt: string
  event: string
  userId: string | null
  accountId: string | null
  transactionId: string | null
  ip: string | null
  traceId: string | null
  detail: string | null
}

export interface CustomerView {
  id: string
  name: string
  document: string
  email: string
  phone: string
  status: string
  createdAt: string
}

export interface AccountAdmin {
  id: string
  customerId: string
  branch: string
  accountNumber: string
  type: 'CHECKING' | 'SAVINGS'
  status: 'ACTIVE' | 'BLOCKED' | 'CLOSED'
}

export interface LimitAdmin {
  type: string
  perOperation: Money
  daily: Money
  usedToday: Money
}

export interface FxRateAdmin {
  currency: string
  mid: string
  buyRate: string
  sellRate: string
  spreadPercent: string
  updatedAt: string
}

export interface StaffUser {
  id: string
  email: string
  role: 'ADMIN' | 'SUPPORT'
  status: string
}

export const staffApi = {
  audit: (page: number, event?: string, userId?: string) => {
    const q = new URLSearchParams({ page: String(page), size: '20' })
    if (event) q.set('event', event)
    if (userId) q.set('userId', userId)
    return api<Page<AuditLog>>(`/audit?${q}`)
  },
  customer: (id: string) => api<CustomerView>(`/admin/customers/${id}`),
  searchCustomers: (q: string, page: number) =>
    api<Page<CustomerView>>(`/admin/customers?${new URLSearchParams({ q, page: String(page), size: '10' })}`),
  customerAccounts: (id: string) => api<AccountAdmin[]>(`/admin/customers/${id}/accounts`),
  account: (id: string) => api<AccountAdmin>(`/admin/accounts/${id}`),
  limits: (accountId: string) => api<LimitAdmin[]>(`/admin/accounts/${accountId}/limits`),
  changeLimit: (accountId: string, type: string, perOperation: string, daily: string) =>
    api<unknown>(`/admin/accounts/${accountId}/limits/${type}`, { method: 'PUT', body: { perOperation, daily } }),
  block: (accountId: string) => api<unknown>(`/admin/accounts/${accountId}/block`, { method: 'POST' }),
  unblock: (accountId: string) => api<unknown>(`/admin/accounts/${accountId}/unblock`, { method: 'POST' }),
  fxRates: () => api<FxRateAdmin[]>('/admin/fx/rates'),
  changeFxRate: (currency: string, mid: string, spreadPercent: string) =>
    api<FxRateAdmin>(`/admin/fx/rates/${currency}`, { method: 'PUT', body: { mid, spreadPercent } }),
  users: () => api<StaffUser[]>('/admin/users'),
  createUser: (email: string, password: string, role: 'ADMIN' | 'SUPPORT') =>
    api<StaffUser>('/admin/users', { method: 'POST', body: { email, password, role } }),
  disableUser: (id: string) => api<void>(`/admin/users/${id}/disable`, { method: 'POST' }),
  enableUser: (id: string) => api<void>(`/admin/users/${id}/enable`, { method: 'POST' }),
}
