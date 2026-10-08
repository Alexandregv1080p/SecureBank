import { api } from '../lib/api'
import type { Investment, InvestmentProduct } from './types'

const idem = (key: string) => ({ 'Idempotency-Key': key })

export const investmentApi = {
  products: () => api<InvestmentProduct[]>('/investments/products'),
  list: () => api<Investment[]>('/investments'),
  get: (id: string) => api<Investment>(`/investments/${id}`),
  apply: (body: { accountId: string; productCode: string; amount: string }, key: string) =>
    api<Investment>('/investments', { method: 'POST', body, headers: idem(key) }),
  /** [amount] é o LÍQUIDO que a pessoa quer receber; ausente resgata tudo (valor igual ou maior que o líquido também). */
  redeem: (id: string, amount: string | undefined, key: string) =>
    api<Investment>(`/investments/${id}/redeem`, { method: 'POST', body: amount ? { amount } : {}, headers: idem(key) }),
}
