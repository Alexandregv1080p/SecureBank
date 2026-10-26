import { api } from '../lib/api'
import type { Department, StaffLevel, StaffRole } from '../lib/staff'
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
  role: StaffRole
  department: Department
  level: StaffLevel
  status: string
  mfaEnabled: boolean
}

export interface Approval {
  id: string
  kind: 'CREDIT_LIMIT' | 'ACCOUNT_UNBLOCK'
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELED'
  subjectId: string
  subject: string
  customerId: string
  amount: string | null
  /** Limite do cartão antes do pedido (só no pedido de limite). */
  currentLimit: string | null
  note: string | null
  department: Department
  requestedBy: string
  createdAt: string
  decidedBy: string | null
  decisionNote: string | null
  decidedAt: string | null
  canDecide: boolean
  canCancel: boolean
}

/** O que volta de uma ação com alçada: feita na hora (formato antigo) ou virou pedido (um Approval). */
export type LimitResult = { cardId: string; creditLimit: Money } | Approval
export type UnblockResult = { id: string; status: string } | Approval

export interface Overview {
  generatedAt: string
  days: number
  customers: { total: number; active: number; newInPeriod: number }
  accounts: { active: number; blocked: number; closed: number; totalBalance: string }
  activity: {
    days: { date: string; count: number; volume: string }[]
    byType: { type: string; count: number; volume: string }[]
    totalCount: number
    totalVolume: string
  }
  credit: { cards: number; totalLimit: string; used: string; overdueInvoices: number; overdueAmount: string; lateCharges: string }
  security: { failedLogins24h: number; accessDenied24h: number; outboxPending: number }
}

export interface CreditCardAdmin {
  id: string
  accountId: string
  last4: string
  status: 'ACTIVE' | 'BLOCKED' | 'CANCELED'
  dueDay: number
  creditLimit: Money
  used: Money
  available: Money
  createdAt: string
}

export interface CardAdmin {
  id: string
  accountId: string
  kind: 'DEBIT' | 'CREDIT'
  holderName: string
  maskedNumber: string
  last4: string
  status: 'ACTIVE' | 'BLOCKED' | 'CANCELED'
  statusReason: string | null
  createdAt: string
}

export const staffApi = {
  cards: (customerId: string) => api<CardAdmin[]>(`/admin/customers/${customerId}/cards`),
  blockCard: (cardId: string, reason: string) => api<CardAdmin>(`/admin/cards/${cardId}/block`, { method: 'POST', body: { reason } }),
  unblockCard: (cardId: string) => api<CardAdmin>(`/admin/cards/${cardId}/unblock`, { method: 'POST' }),
  creditCards: (customerId: string) => api<CreditCardAdmin[]>(`/admin/customers/${customerId}/credit-cards`),
  setCreditLimit: (cardId: string, limit: string, note?: string) => api<LimitResult>(`/admin/credit/cards/${cardId}/limit`, { method: 'PUT', body: { limit, note } }),
  overview: (days: number) => api<Overview>(`/admin/overview?days=${days}`),
  audit: (page: number, event?: string, userId?: string, from?: string, to?: string) => {
    const q = new URLSearchParams({ page: String(page), size: '20' })
    if (from) q.set('from', from)
    if (to) q.set('to', to)
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
  block: (accountId: string, reason: string) => api<unknown>(`/admin/accounts/${accountId}/block`, { method: 'POST', body: { reason } }),
  unblock: (accountId: string, note?: string) => api<UnblockResult>(`/admin/accounts/${accountId}/unblock`, { method: 'POST', body: { note } }),
  fxRates: () => api<FxRateAdmin[]>('/admin/fx/rates'),
  changeFxRate: (currency: string, mid: string, spreadPercent: string) =>
    api<FxRateAdmin>(`/admin/fx/rates/${currency}`, { method: 'PUT', body: { mid, spreadPercent } }),
  users: () => api<StaffUser[]>('/admin/users'),
  createUser: (email: string, password: string, department: Department, level: StaffLevel) =>
    api<StaffUser>('/admin/users', { method: 'POST', body: { email, password, department, level } }),
  assignUser: (id: string, department: Department, level: StaffLevel) =>
    api<StaffUser>(`/admin/users/${id}/assignment`, { method: 'PUT', body: { department, level } }),
  approvals: (status?: Approval['status']) => api<Approval[]>(`/admin/approvals${status ? `?status=${status}` : ''}`),
  pendingApprovals: () => api<{ pending: number }>('/admin/approvals/pending-count'),
  approve: (id: string, note?: string) => api<Approval>(`/admin/approvals/${id}/approve`, { method: 'POST', body: { note } }),
  reject: (id: string, note: string) => api<Approval>(`/admin/approvals/${id}/reject`, { method: 'POST', body: { note } }),
  cancelApproval: (id: string) => api<Approval>(`/admin/approvals/${id}/cancel`, { method: 'POST' }),
  disableUser: (id: string) => api<void>(`/admin/users/${id}/disable`, { method: 'POST' }),
  enableUser: (id: string) => api<void>(`/admin/users/${id}/enable`, { method: 'POST' }),
}
