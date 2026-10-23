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

export type ReasonAction = 'block' | 'cancel'
export const REASON_MIN = 3
export const REASON_MAX = 140

export interface ReasonOption {
  id: string
  label: string
  /** "Outro motivo": o cliente precisa contar com as próprias palavras. */
  other?: boolean
}

/** Motivos mais comuns para bloquear (temporário) e cancelar (definitivo); o texto escolhido segue para o servidor. */
export const reasonOptions: Record<ReasonAction, ReasonOption[]> = {
  block: [
    { id: 'lost', label: 'Perdi o controle do cartão' },
    { id: 'unknown', label: 'Vi uma compra que não reconheço' },
    { id: 'pause', label: 'Quero pausar o uso por um tempo' },
    { id: 'other', label: 'Outro motivo', other: true },
  ],
  cancel: [
    { id: 'stolen', label: 'O cartão foi perdido ou roubado' },
    { id: 'fraud', label: 'Fui vítima de fraude ou golpe' },
    { id: 'unused', label: 'Não preciso mais deste cartão' },
    { id: 'replace', label: 'Quero trocar por um cartão novo' },
    { id: 'other', label: 'Outro motivo', other: true },
  ],
}

/** O motivo é obrigatório: uma opção e, em "Outro motivo", o relato (de 3 a 140 caracteres, sem quebra de linha). */
export function reasonIssue(option: ReasonOption | undefined, details: string): string | null {
  if (!option) return 'Escolha o motivo'
  const text = details.trim()
  if (option.other) {
    if (text.length < REASON_MIN) return `Conte o motivo em pelo menos ${REASON_MIN} caracteres`
    if (text.length > REASON_MAX) return `No máximo ${REASON_MAX} caracteres`
    return null
  }
  return composeReason(option, details).length > REASON_MAX ? `No máximo ${REASON_MAX} caracteres no total` : null
}

/** Texto enviado ao servidor: o relato, em "Outro motivo"; senão a opção, com o detalhe depois dos dois pontos. */
export function composeReason(option: ReasonOption, details: string): string {
  const text = details.trim().replace(/\s+/g, ' ')
  if (option.other) return text
  return text ? `${option.label}: ${text}` : option.label
}
