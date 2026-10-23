import type { Invoice, InvoiceStatus } from '../services/types'
import { formatBRL, parseAmount } from './money'
import { amountIssue } from './validation'
import { merchantIssue } from './card'

export const DUE_DAYS = [5, 10, 15, 20, 25]
export const MAX_INSTALLMENTS = 12
/** Parcela mínima em centavos (R$ 5,00), como no servidor. */
export const MIN_INSTALLMENT_CENTS = 500
export const CREDIT_LIMIT_START = 'R$ 2.000,00'

export const invoiceStatusLabel: Record<InvoiceStatus, string> = {
  OPEN: 'Aberta',
  CLOSED: 'Fechada',
  OVERDUE: 'Vencida',
  PAID: 'Paga',
}

const months = ['janeiro', 'fevereiro', 'março', 'abril', 'maio', 'junho', 'julho', 'agosto', 'setembro', 'outubro', 'novembro', 'dezembro']

/** "2026-11" vira "novembro de 2026". */
export function monthLabel(month: string): string {
  const [year, m] = month.split('-').map(Number)
  return `${months[m - 1] ?? month} de ${year}`
}

/** "2026-11-10" vira "10/11" (as datas da fatura vêm como dia, sem fuso). */
export function shortDate(day: string): string {
  const [, m, d] = day.split('-')
  return `${d}/${m}`
}

/** Divide em parcelas de centavos inteiros; o resto vai para a última (igual ao servidor). */
export function installmentAmounts(totalCents: number, n: number): number[] {
  const each = Math.floor(totalCents / n)
  return [...Array(n - 1).fill(each), totalCents - each * (n - 1)]
}

/** Opções "3x de R$ 100,00" que respeitam a parcela mínima, para o valor informado. */
export function installmentOptions(amount: string): { n: number; label: string }[] {
  const value = parseAmount(amount)
  if (value === null) return [{ n: 1, label: 'À vista' }]
  const cents = Math.round(Number(value) * 100)
  const options = [{ n: 1, label: `À vista, ${formatBRL(value)}` }]
  for (let n = 2; n <= MAX_INSTALLMENTS; n++) {
    const parts = installmentAmounts(cents, n)
    if (parts[0] < MIN_INSTALLMENT_CENTS) break
    const last = parts[n - 1]
    options.push({ n, label: `${n}x de ${formatBRL((parts[0] / 100).toFixed(2))}${last !== parts[0] ? ' (última ' + formatBRL((last / 100).toFixed(2)) + ')' : ''}, sem juros` })
  }
  return options
}

/** Valida a compra no crédito antes de enviar (o servidor confere de novo). */
export function creditPurchaseIssues(amount: string, merchant: string, installments: number, available?: string): { amount?: string; merchant?: string } {
  const issues: { amount?: string; merchant?: string } = {}
  const value = parseAmount(amount)
  if (value === null) issues.amount = amountIssue(amount) ?? undefined
  else if (available !== undefined && Math.round(Number(value) * 100) > Math.round(Number(available) * 100)) {
    issues.amount = `Passa do limite disponível (${formatBRL(available)})`
  } else if (installments > 1 && installmentAmounts(Math.round(Number(value) * 100), installments)[0] < MIN_INSTALLMENT_CENTS) {
    issues.amount = 'A parcela mínima é R$ 5,00: use menos parcelas'
  }
  const merchantProblem = merchantIssue(merchant)
  if (merchantProblem) issues.merchant = merchantProblem
  return issues
}

/** Valor a pagar da fatura: positivo e, no máximo, o que falta. */
export function payIssue(amount: string, remaining: string): string | null {
  const value = parseAmount(amount)
  if (value === null) return amountIssue(amount)
  if (Math.round(Number(value) * 100) > Math.round(Number(remaining) * 100)) return `O valor passa do que falta na fatura (${formatBRL(remaining)})`
  return null
}

/** O que mostrar sobre a fatura aberta ou a mais urgente. */
export function dueHint(invoice: Pick<Invoice, 'status' | 'dueDate' | 'closingDate'>): string {
  switch (invoice.status) {
    case 'OPEN':
      return `Fecha em ${shortDate(invoice.closingDate)} e vence em ${shortDate(invoice.dueDate)}`
    case 'CLOSED':
      return `Fechada: vence em ${shortDate(invoice.dueDate)}`
    case 'OVERDUE':
      return `Venceu em ${shortDate(invoice.dueDate)}: pague para voltar a comprar`
    default:
      return 'Fatura paga'
  }
}
