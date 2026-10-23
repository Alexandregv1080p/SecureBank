import type { CardStatus, LimitUsage } from '../services/types'
import { parseAmount } from './money'
import { amountIssue, balanceIssue, limitIssue } from './validation'

export const MAX_OPEN_PER_ACCOUNT = 3
export const MERCHANT_MAX = 60

/** "9123456789012345" → "9123 4567 8901 2345" (em grupos de 4, como no plástico). */
export function formatCardNumber(number: string): string {
  return number.replace(/\D/g, '').replace(/(\d{4})(?=\d)/g, '$1 ')
}

/** Validade como no cartão: mês com dois dígitos e ano com dois (07/31). */
export function formatExpiry(month: number, year: number): string {
  return `${String(month).padStart(2, '0')}/${String(year % 100).padStart(2, '0')}`
}

export const statusLabel: Record<CardStatus, string> = {
  ACTIVE: 'Ativo',
  BLOCKED: 'Bloqueado',
  CANCELED: 'Cancelado',
}

/** Estabelecimento da compra: obrigatório, até 60 caracteres, sem quebra de linha nem caracteres de controle. */
export function merchantIssue(input: string): string | null {
  const name = input.trim()
  if (!name) return 'Informe o estabelecimento'
  if (name.length > MERCHANT_MAX) return `No máximo ${MERCHANT_MAX} caracteres`
  // eslint-disable-next-line no-control-regex
  if (/[\u0000-\u001f\u007f-\u009f\u2028\u2029\u202a-\u202e\u2066-\u2069]/.test(name)) return 'Use só letras, números e símbolos comuns'
  return null
}

/** Valida a compra antes de enviar: valor, saldo da conta e limite do cartão (o servidor confere tudo de novo). */
export function purchaseIssues(amount: string, merchant: string, balance?: string, limit?: Pick<LimitUsage, 'perOperation' | 'remainingToday'>): { amount?: string; merchant?: string } {
  const issues: { amount?: string; merchant?: string } = {}
  const value = parseAmount(amount)
  const amountProblem = value === null ? amountIssue(amount) : (balance !== undefined ? balanceIssue(value, balance) : null) ?? limitIssue(value, limit)
  if (amountProblem) issues.amount = amountProblem
  const merchantProblem = merchantIssue(merchant)
  if (merchantProblem) issues.merchant = merchantProblem
  return issues
}
