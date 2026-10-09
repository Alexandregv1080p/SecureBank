import { parseAmount } from './money'
import { amountIssue } from './validation'
import type { ChargeStatus, PixKeyType, ScheduleStatus } from '../services/pix'

export const keyTypeLabel = (type: PixKeyType | string) =>
  ({ CPF: 'CPF', EMAIL: 'E-mail', PHONE: 'Celular', RANDOM: 'Chave aleatória' })[type] ?? type

export const keyTypes: PixKeyType[] = ['CPF', 'EMAIL', 'PHONE', 'RANDOM']
export const MAX_KEYS = 5

export const chargeStatusLabel = (status: ChargeStatus) =>
  ({ ACTIVE: 'Aguardando pagamento', PAID: 'Paga', CANCELED: 'Cancelada', EXPIRED: 'Expirada' })[status]

export const scheduleStatusLabel = (status: ScheduleStatus) =>
  ({ SCHEDULED: 'Agendado', EXECUTED: 'Realizado', FAILED: 'Não realizado', CANCELED: 'Cancelado' })[status]

/** Por que um Pix agendado não foi realizado (códigos do servidor), em linguagem de gente. */
export function scheduleFailure(code: string | null): string {
  switch (code) {
    case 'INSUFFICIENT_FUNDS':
      return 'saldo insuficiente'
    case 'LIMIT_EXCEEDED':
      return 'limite do Pix excedido'
    case 'NOT_FOUND':
      return 'a chave não existe mais'
    case 'ACCOUNT_NOT_ACTIVE':
      return 'conta indisponível'
    case 'CUSTOMER_NOT_ACTIVE':
      return 'cadastro inativo'
    case null:
      return 'motivo desconhecido'
    default:
      return code
  }
}

export const MAX_SCHEDULE_DAYS = 365
const DAY = 24 * 60 * 60 * 1000

/** "AAAA-MM-DD" no calendário local. */
export const isoDay = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`

/** Datas aceitas para agendar: de amanhã até 365 dias à frente (mesma regra do servidor). */
export function scheduleBounds(today: Date): { min: string; max: string } {
  const base = new Date(today.getFullYear(), today.getMonth(), today.getDate())
  return { min: isoDay(new Date(base.getTime() + DAY)), max: isoDay(new Date(base.getTime() + MAX_SCHEDULE_DAYS * DAY)) }
}

export function scheduleDateError(value: string, today: Date): string | null {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return 'Escolha a data'
  const { min, max } = scheduleBounds(today)
  if (value < min) return 'Escolha uma data a partir de amanhã'
  if (value > max) return `No máximo ${MAX_SCHEDULE_DAYS} dias à frente`
  return null
}

/** "2026-10-20" → "20/10/2026". */
export const displayDay = (iso: string) => (/^\d{4}-\d{2}-\d{2}$/.test(iso) ? `${iso.slice(8)}/${iso.slice(5, 7)}/${iso.slice(0, 4)}` : iso)

/** Quanto se pode devolver: positivo e no máximo o que ainda resta (o servidor confere de novo). */
export function refundAmountError(input: string, refundable: string): string | null {
  const parsed = parseAmount(input)
  if (parsed === null) return amountIssue(input)
  return Math.round(Number(parsed) * 100) > Math.round(Number(refundable) * 100) ? 'O valor passa do que ainda pode ser devolvido' : null
}

/** Validade da cobrança, em minutos (o servidor aceita de 1 minuto a 7 dias). */
export const chargeValidities = [
  { label: '1 hora', minutes: 60 },
  { label: '1 dia', minutes: 1440 },
  { label: '7 dias', minutes: 10080 },
] as const

/** Tela de envio: a chave digitada ou o código colado precisa ter algo antes de consultar. */
export const keyInputError = (input: string) => (input.trim().length < 3 ? 'Digite a chave ou cole o Pix Copia e Cola.' : null)
