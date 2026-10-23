import { api } from '../lib/api'
import type { Card, CreditPurchase, CreditSummary, Invoice, InvoiceDetail, Page, RefinanceOffer, RefinancePlan } from './types'

const idem = (key: string) => ({ 'Idempotency-Key': key })

/** Cartão de crédito. Número, CVV, bloquear e cancelar são os mesmos do débito (`cardApi`). */
export const creditApi = {
  /** Emite o cartão com o limite inicial; `dueDay`: 5, 10, 15, 20 ou 25. */
  issue: (accountId: string, dueDay: number) => api<Card & { dueDay: number }>('/credit/cards', { method: 'POST', body: { accountId, dueDay } }),
  /** Cartão adicional: número próprio, mesmo limite e mesmas faturas do principal. */
  addAdditional: (mainId: string, holderName: string) => api<Card>(`/credit/cards/${mainId}/additional`, { method: 'POST', body: { holderName } }),
  changeLimit: (id: string, limit: string) => api<CreditSummary>(`/credit/cards/${id}/limit`, { method: 'PUT', body: { limit } }),
  refinanceOptions: (id: string, month: string) => api<RefinanceOffer>(`/credit/cards/${id}/invoices/${month}/refinance`),
  refinance: (id: string, month: string, installments: number, key: string) =>
    api<{ invoice: Invoice; purchase: CreditPurchase; plan: RefinancePlan }>(`/credit/cards/${id}/invoices/${month}/refinance`, { method: 'POST', body: { installments }, headers: idem(key) }),
  summary: (id: string) => api<CreditSummary>(`/credit/cards/${id}`),
  invoices: (id: string) => api<Invoice[]>(`/credit/cards/${id}/invoices`),
  invoice: (id: string, month: string) => api<InvoiceDetail>(`/credit/cards/${id}/invoices/${month}`),
  purchase: (id: string, body: { amount: string; merchant: string; installments: number }, key: string) =>
    api<CreditPurchase>(`/credit/cards/${id}/purchases`, { method: 'POST', body, headers: idem(key) }),
  purchases: (id: string, page = 0, size = 20) => api<Page<CreditPurchase>>(`/credit/cards/${id}/purchases?page=${page}&size=${size}`),
  payInvoice: (id: string, month: string, amount: string, key: string) =>
    api<Invoice>(`/credit/cards/${id}/invoices/${month}/payments`, { method: 'POST', body: { amount }, headers: idem(key) }),
}
