import { api } from '../lib/api'
import type { FxWallet, Money, Page } from './types'

/** Cotação simulada: o cliente COMPRA pelo [buyRate] e VENDE pelo [sellRate] (reais por 1 unidade). */
export interface FxRate {
  currency: string
  mid: string
  buyRate: string
  sellRate: string
  spreadPercent: string
  updatedAt: string
}

/** Recibo de uma compra (BUY) ou venda (SELL): [rate] é a cotação aplicada, [brlAmount] o que saiu ou entrou em reais. */
export interface FxOperation {
  id: string
  accountId: string
  side: 'BUY' | 'SELL'
  foreignAmount: Money
  rate: string
  brlAmount: Money
  createdAt: string
}

const idem = (key: string) => ({ 'Idempotency-Key': key })

export const fxApi = {
  rates: () => api<FxRate[]>('/fx/rates'),
  wallets: () => api<FxWallet[]>('/fx/wallets'),
  operations: (page = 0, size = 10) => api<Page<FxOperation>>(`/fx/operations?page=${page}&size=${size}`),
  /** [quotedRate] é a cotação que a pessoa VIU: se mudou no servidor, ele recusa (FX_RATE_CHANGED) e nada acontece. */
  trade: (side: 'buy' | 'sell', body: { accountId: string; currency: string; amount: string; quotedRate: string }, key: string) =>
    api<FxOperation>(`/fx/${side}`, { method: 'POST', body, headers: idem(key) }),
}
