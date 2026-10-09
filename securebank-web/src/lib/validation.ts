import { formatBRL, parseAmount } from './money'
import type { LimitUsage } from '../services/types'

/**
 * Regras de campo compartilhadas pelos formulários (espelham securebank-android/.../core/util/Validation.kt e o servidor).
 * Só poupam a ida e volta: o servidor confere tudo de novo.
 */

/**
 * Aplica a máscara ANTES do react-hook-form ler o evento: mudar `e.target.value` num `onChange` do `register` chega tarde
 * (o formulário já guardou o texto cru, e o campo mostraria uma coisa e validaria outra).
 */
export function withMask<R extends { onChange: (e: { target: any; type?: any }) => Promise<void | boolean> }>(reg: R, mask: (v: string) => string): R {
  return { ...reg, onChange: (e) => { e.target.value = mask(e.target.value); return reg.onChange(e) } }
}

/** Formato 123456-7 E dígito verificador (módulo 11, pesos 2..9 da direita), igual a AccountNumber.generate do servidor. */
export function accountNumberValid(number: string): boolean {
  if (!/^\d{6,12}-\d$/.test(number)) return false
  const digits = number.split('-')[0]
  let sum = 0
  let weight = 2
  for (let i = digits.length - 1; i >= 0; i--) {
    sum += Number(digits[i]) * weight
    weight = weight === 9 ? 2 : weight + 1
  }
  const remainder = sum % 11
  return (remainder < 2 ? 0 : 11 - remainder) === Number(number.slice(-1))
}

/** Só os números: o hífen entra sozinho antes do último dígito (o verificador), então 1234560 vira 123456-0. */
export function maskAccountNumber(raw: string): string {
  const d = raw.replace(/\D/g, '').slice(0, 13)
  return d.length <= 6 ? d : `${d.slice(0, -1)}-${d.slice(-1)}`
}

/** A conta de destino é a própria conta de origem? */
export function sameAccount(source: { branch: string; accountNumber: string }, branch: string, number: string): boolean {
  return source.branch === branch && source.accountNumber === number
}

/** Deixa digitar só um valor em reais: dígitos e uma vírgula, até 2 casas e 13 dígitos inteiros ("." vira vírgula, a menos que seja milhar). */
export function sanitizeAmount(raw: string): string {
  let text = raw.replace(/[^\d,.]/g, '')
  text = text.includes(',') ? text.replace(/\./g, '') : text.replace('.', ',').replace(/\./g, '')
  const comma = text.indexOf(',')
  const head = (comma >= 0 ? text.slice(0, comma) : text).replace(/^0+/, '').slice(0, 13)
  const integer = head || (comma >= 0 || text ? '0' : '')
  if (comma < 0) return integer
  return `${integer},${text.slice(comma + 1).replace(/\D/g, '').slice(0, 2)}`
}

/** Por que o valor digitado não serve (mensagem específica), ou null se é um valor positivo com até 2 casas. */
export function amountIssue(input: string): string | null {
  const text = input.replace(/\s|R\$/g, '')
  if (!text) return 'Informe o valor'
  if (parseAmount(input) !== null) return null
  const normalized = text.includes(',') ? text.replace(/\./g, '').replace(/,/g, '.') : text
  if (!/^\d+(\.\d*)?$/.test(normalized)) return 'Use só números e vírgula para os centavos, ex.: 1250,50'
  const [int, dec = ''] = normalized.split('.')
  if (dec.length > 2) return 'Use no máximo 2 casas decimais'
  if (int.length > 13) return 'Esse valor é alto demais'
  return 'O valor deve ser maior que zero'
}

/** `amount` já normalizado ("1234.56"). */
export function balanceIssue(amount: string, balance: string): string | null {
  const a = Number(amount)
  const b = Number(balance)
  if (!Number.isFinite(a) || !Number.isFinite(b)) return null
  return Math.round(a * 100) > Math.round(b * 100) ? `Saldo insuficiente: você tem ${formatBRL(balance)} nesta conta` : null
}

/** Limite por operação e o que resta hoje (`amount` normalizado). Sem limite carregado, não opina (o servidor decide). */
export function limitIssue(amount: string, usage?: Pick<LimitUsage, 'perOperation' | 'remainingToday'> | null): string | null {
  if (!usage) return null
  const cents = Math.round(Number(amount) * 100)
  if (!Number.isFinite(cents)) return null
  if (cents > Math.round(Number(usage.perOperation.amount) * 100)) return `Acima do limite por operação de ${formatBRL(usage.perOperation.amount)}`
  if (cents > Math.round(Number(usage.remainingToday.amount) * 100)) return `Acima do que resta do limite de hoje (${formatBRL(usage.remainingToday.amount)})`
  return null
}

export function cpfValid(input: string): boolean {
  const d = input.replace(/\D/g, '')
  if (d.length !== 11 || new Set(d).size === 1) return false
  const check = (length: number) => {
    let sum = 0
    for (let i = 0; i < length; i++) sum += Number(d[i]) * (length + 1 - i)
    const r = (sum * 10) % 11
    return r === 10 ? 0 : r
  }
  return check(9) === Number(d[9]) && check(10) === Number(d[10])
}

export function maskCpf(raw: string): string {
  const d = raw.replace(/\D/g, '').slice(0, 11)
  return [...d].map((c, i) => (i === 3 || i === 6 ? '.' : i === 9 ? '-' : '') + c).join('')
}

/** (11) 99999-8888 ou (11) 9999-8888, conforme os dígitos; aceita colar com +55. */
export function maskPhone(raw: string): string {
  let d = raw.replace(/\D/g, '')
  if (d.length > 11 && d.startsWith('55')) d = d.slice(2)
  d = d.slice(0, 11)
  if (!d) return ''
  const ddd = d.slice(0, 2)
  const rest = d.slice(2)
  const body = rest.length <= 4 ? rest : rest.length <= 8 ? `${rest.slice(0, 4)}-${rest.slice(4)}` : `${rest.slice(0, 5)}-${rest.slice(5)}`
  return ddd.length < 2 ? `(${ddd}` : `(${ddd}) ${body}`.trimEnd()
}

export function fullNameIssue(name: string): string | null {
  const words = name.trim().split(/\s+/).filter(Boolean)
  if (words.length < 2) return 'Informe nome e sobrenome'
  if (name.length > 120) return 'Use no máximo 120 caracteres'
  if (!words.every((w) => /^[\p{L}'.-]+$/u.test(w))) return 'Use só letras no nome'
  return null
}

const COMMON_PASSWORDS = new Set([
  'password1234', '123456789012', 'qwertyuiop12', '1234567890ab', 'senha1234567', 'senhasenha123', 'passwordpassword',
  'letmein12345', 'iloveyou1234', 'admin1234567', 'welcome12345', 'changeme1234', 'abcdefghijkl', '111111111111', '000000000000',
])

/** Mesma política do servidor (PasswordPolicy): tamanho, não ser previsível e não conter o e-mail. */
export function passwordIssue(password: string, email?: string): string | null {
  if (password.length < 12 || password.length > 128) return 'Use de 12 a 128 caracteres'
  const lower = password.toLowerCase()
  if (COMMON_PASSWORDS.has(lower) || new Set(lower).size < 5) return 'Essa senha é fácil de adivinhar'
  const local = (email ?? '').trim().toLowerCase().split('@')[0]
  if (local.length >= 4 && lower.includes(local)) return 'A senha não pode conter o seu e-mail'
  return null
}
