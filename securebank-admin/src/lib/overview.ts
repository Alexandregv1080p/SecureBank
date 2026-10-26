import type { Share } from './dashboard'
import type { Overview } from '../services/staff'

export const transactionTypeLabel: Record<string, string> = {
  DEPOSIT: 'Depósito',
  WITHDRAW: 'Saque',
  TRANSFER: 'Transferência',
  PAYMENT: 'Pagamento de boleto',
  REFUND: 'Estorno',
  PIGGY_IN: 'Guardado em porquinho',
  PIGGY_OUT: 'Resgate de porquinho',
  PIX_OUT: 'Pix enviado',
  PIX_IN: 'Pix recebido',
  PIX_RETURN_OUT: 'Devolução de Pix',
  PIX_RETURN_IN: 'Devolução recebida',
  INVEST_OUT: 'Aplicação',
  INVEST_IN: 'Resgate de aplicação',
  FX_BUY: 'Compra de moeda',
  FX_SELL: 'Venda de moeda',
  CARD_PURCHASE: 'Compra no débito',
  CARD_INVOICE_PAYMENT: 'Fatura do cartão',
}

export const typeLabel = (type: string) => transactionTypeLabel[type] ?? type

const palette = ['var(--chart-1)', 'var(--chart-2)', 'var(--chart-3)', 'var(--chart-4)', 'var(--chart-5)', 'var(--chart-6)', 'var(--chart-7)', 'var(--chart-8)']

/** Fatias da rosca: os 7 tipos de maior volume e o resto junto em "Outros" (as fatias somam 100%). */
export function typeShares(byType: { type: string; volume: string }[]): Share[] {
  const items = byType.map((t) => ({ type: t.type, value: Number(t.volume) })).filter((t) => t.value > 0).sort((a, b) => b.value - a.value)
  const total = items.reduce((sum, t) => sum + t.value, 0)
  if (total === 0) return []
  const top = items.slice(0, 7)
  const rest = items.slice(7).reduce((sum, t) => sum + t.value, 0)
  const shown = rest > 0 ? [...top, { type: 'OTHERS', value: rest }] : top
  return shown.map((t, i) => ({
    category: t.type,
    label: t.type === 'OTHERS' ? 'Outros' : typeLabel(t.type),
    color: palette[i],
    value: t.value,
    percent: (t.value / total) * 100,
  }))
}

/** Percentual inteiro (0–100) de [part] sobre [whole]; 0 se não há base. */
export function percentOf(part: string | number, whole: string | number): number {
  const w = Number(whole)
  return w > 0 ? Math.min(100, Math.round((Number(part) / w) * 100)) : 0
}

/** "2026-10-23" vira "23/10". */
export const dayLabel = (iso: string) => `${iso.slice(8, 10)}/${iso.slice(5, 7)}`

export interface AttentionItem {
  key: string
  tone: 'danger' | 'warn'
  text: string
  to: string
}

/** O que merece a atenção de quem gerencia agora, do mais grave ao menos (vazio = tudo em ordem). */
export function attentionItems(data: Pick<Overview, 'accounts' | 'credit' | 'security'>): AttentionItem[] {
  const items: AttentionItem[] = []
  const plural = (n: number, one: string, many: string) => `${n} ${n === 1 ? one : many}`
  if (data.credit.overdueInvoices > 0) {
    items.push({ key: 'overdue', tone: 'danger', text: `${plural(data.credit.overdueInvoices, 'fatura vencida com saldo', 'faturas vencidas com saldo')}, R$ ${Number(data.credit.overdueAmount).toLocaleString('pt-BR', { minimumFractionDigits: 2 })} em atraso`, to: '/clientes' })
  }
  if (data.security.accessDenied24h > 0) {
    items.push({ key: 'denied', tone: 'danger', text: `${plural(data.security.accessDenied24h, 'acesso negado', 'acessos negados')} nas últimas 24 h`, to: '/auditoria?evento=ACCESS_DENIED' })
  }
  if (data.security.failedLogins24h > 20) {
    items.push({ key: 'logins', tone: 'danger', text: `${data.security.failedLogins24h} logins recusados nas últimas 24 h (possível tentativa de invasão)`, to: '/auditoria?evento=LOGIN_FAILED' })
  }
  if (data.security.outboxPending > 100) {
    items.push({ key: 'outbox', tone: 'danger', text: `${data.security.outboxPending} eventos parados na fila de publicação: os avisos aos clientes podem estar atrasados`, to: '/auditoria' })
  }
  if (data.accounts.blocked > 0) {
    items.push({ key: 'blocked', tone: 'warn', text: `${plural(data.accounts.blocked, 'conta bloqueada', 'contas bloqueadas')}`, to: '/contas' })
  }
  return items
}
