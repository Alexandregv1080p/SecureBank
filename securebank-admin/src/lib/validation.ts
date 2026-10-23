import { formatBRL, parseAmount } from './money'
import type { Money } from '../services/types'

type LimitUsage = { perOperation: Money; remainingToday: Money }

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

/**
 * Deixa digitar só um valor em reais: dígitos, ponto de milhar e uma vírgula com até 2 casas, até 13 dígitos inteiros.
 * O texto fica como foi digitado ("1.500" continua "1.500"; quem decide o valor é parseAmount). Só reescreve o que é
 * claramente de outro padrão: "1,234.56" (EUA) vira "1234,56" e "1,234,567" vira "1234567".
 */
export function sanitizeAmount(raw: string): string {
  let t = raw.replace(/[^\d,.]/g, '')
  const lastComma = t.lastIndexOf(',')
  const lastDot = t.lastIndexOf('.')
  if (lastComma >= 0 && lastDot > lastComma) {
    t = t.slice(0, lastDot).replace(/,/g, '') + ',' + t.slice(lastDot + 1)
  } else if ((t.match(/,/g) ?? []).length > 1) {
    const groups = t.split(',')
    if (groups.slice(1).every((g) => g.length === 3)) t = groups.join('')
  }
  const comma = t.indexOf(',')
  const decimals = comma >= 0 ? t.slice(comma + 1).replace(/\D/g, '').slice(0, 2) : ''
  let integer = (comma >= 0 ? t.slice(0, comma) : t).replace(/,/g, '')
  let digits = 0
  let kept = ''
  for (const c of integer) {
    if (c !== '.') {
      if (digits === 13) break
      digits++
    }
    kept += c
  }
  integer = kept.replace(/^0+(?=\d)/, '')
  if (integer === '' && (comma >= 0 || t !== '')) integer = '0'
  if (integer.startsWith('.')) integer = '0' + integer
  if (integer === '' ) return comma >= 0 ? `0,${decimals}` : ''
  return comma >= 0 ? `${integer},${decimals}` : integer
}

const AMOUNT_FORMAT = 'Use só números e vírgula para os centavos, ex.: 1.250,50'

/** Por que o valor digitado não serve (mensagem específica), ou null se é um valor positivo com até 2 casas. */
export function amountIssue(input: string): string | null {
  const text = input.replace(/\s|R\$/g, '')
  if (!text) return 'Informe o valor'
  if (parseAmount(input) !== null) return null
  if (/[^\d,.]/.test(text)) return AMOUNT_FORMAT
  const digitsOnly = text.replace(/\D/g, '')
  if (/^0*$/.test(digitsOnly)) return 'O valor deve ser maior que zero'
  const comma = text.lastIndexOf(',')
  if (comma >= 0) {
    if (text.indexOf(',') !== comma) return AMOUNT_FORMAT
    if (text.slice(comma + 1).length > 2) return 'Use no máximo 2 casas decimais'
  } else {
    const dot = text.lastIndexOf('.')
    if (dot >= 0 && text.indexOf('.') === dot && text.slice(dot + 1).length > 3) return 'Use no máximo 2 casas decimais'
  }
  const integerDigits = (comma >= 0 ? text.slice(0, comma) : text.includes('.') && /^\d+\.\d{1,2}$/.test(text) ? text.split('.')[0] : text).replace(/\D/g, '')
  if (integerDigits.length > 13) return 'Esse valor é alto demais'
  return AMOUNT_FORMAT
}

/** Teto de um depósito (simulado), igual ao do servidor. */
export const MAX_DEPOSIT = '100000.00'

/** Depósito acima do teto por operação (`amount` normalizado). */
export function depositIssue(amount: string): string | null {
  return Math.round(Number(amount) * 100) > Math.round(Number(MAX_DEPOSIT) * 100) ? `Um depósito é de até ${formatBRL(MAX_DEPOSIT)} por vez` : null
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
  // letras (com acento), apóstrofo reto ou tipográfico (D’Ávila, comum no teclado do celular), hífen e ponto (Jr.)
  if (!words.every((w) => /^[\p{L}\p{M}'’ʼ.-]+$/u.test(w))) return 'Use só letras no nome'
  return null
}

const COMMON_PASSWORDS = new Set([
  'password1234', '123456789012', 'qwertyuiop12', '1234567890ab', 'senha1234567', 'senhasenha123', 'passwordpassword',
  'letmein12345', 'iloveyou1234', 'admin1234567', 'welcome12345', 'changeme1234', 'abcdefghijkl', '111111111111', '000000000000',
  'mudar1234567', 'brasil123456', '123456789abc', 'qazwsxedcrfv', '1q2w3e4r5t6y', 'q1w2e3r4t5y6',
])
const BAD_WORDS = ['password', 'passwd', 'senha', 'admin', 'qwerty', 'letmein', 'welcome', 'iloveyou', 'changeme', 'master', 'login', 'brasil', 'futebol', 'mudar', 'secure']
const KEYBOARD_ROWS = ['qwertyuiop', 'asdfghjkl', 'zxcvbnm', 'qazwsxedcrfv']

/** "abcabcabc": a senha inteira é uma unidade curta repetida 2 vezes ou mais. */
function repeatsAUnit(s: string): boolean {
  const n = s.length
  for (let unit = 1; unit <= n / 2; unit++) if (n % unit === 0 && s.slice(0, unit).repeat(n / unit) === s) return true
  return false
}

/** Previsível: lista de comuns, poucos caracteres diferentes, só dígitos, unidade repetida, ou mais da metade é sequência/teclado/palavra óbvia. */
function isTooEasy(lower: string): boolean {
  if (COMMON_PASSWORDS.has(lower) || new Set(lower).size < 6) return true
  const s = [...lower].filter((c) => /[\p{L}\p{Nd}]/u.test(c)).join('')
  if (s === '' || /^\p{Nd}+$/u.test(s) || repeatsAUnit(lower)) return true
  const covered = new Array<boolean>(s.length).fill(false)
  for (const step of [1, -1, 0]) {
    let i = 0
    while (i < s.length) {
      let j = i
      while (j + 1 < s.length && s.charCodeAt(j + 1) - s.charCodeAt(j) === step) j++
      if (j - i + 1 >= 4) for (let k = i; k <= j; k++) covered[k] = true
      i = j + 1
    }
  }
  for (const row of KEYBOARD_ROWS) {
    for (const r of [row, [...row].reverse().join('')]) {
      for (let i = 0; i < s.length; i++) {
        let len = 0
        while (i + len < s.length && r.includes(s.slice(i, i + len + 1))) len++
        if (len >= 4) for (let k = i; k < i + len; k++) covered[k] = true
      }
    }
  }
  for (const word of BAD_WORDS) {
    for (let i = s.indexOf(word); i >= 0; i = s.indexOf(word, i + 1)) for (let k = i; k < i + word.length; k++) covered[k] = true
  }
  return covered.filter(Boolean).length * 2 > s.length
}

const EMAIL_ATOM = "[a-z0-9!#$%&'*+/=?^_`{|}~-]+"
const EMAIL = new RegExp(`^${EMAIL_ATOM}(\\.${EMAIL_ATOM})*@([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,24}$`)

/** Mesmo formato que o servidor exige no cadastro: sem ponto dobrado ou nas pontas, domínio com TLD de letras. */
export function emailIssue(email: string): string | null {
  const v = email.trim().toLowerCase()
  if (v === '') return 'Informe o e-mail'
  if (v.length > 254 || v.indexOf('@') > 64 || !EMAIL.test(v)) return 'Informe um e-mail válido, como nome@exemplo.com'
  return null
}

const mod10 = (s: string) => {
  let total = 0
  let weight = 2
  for (let i = s.length - 1; i >= 0; i--) {
    const p = Number(s[i]) * weight
    total += Math.floor(p / 10) + (p % 10)
    weight = weight === 2 ? 1 : 2
  }
  return (10 - (total % 10)) % 10
}
const mod11 = (s: string, convenio: boolean) => {
  let total = 0
  let weight = 2
  for (let i = s.length - 1; i >= 0; i--) {
    total += Number(s[i]) * weight
    weight = weight === 9 ? 2 : weight + 1
  }
  const rest = total % 11
  if (convenio) return rest <= 1 ? 0 : 11 - rest
  const dv = 11 - rest
  return dv === 0 || dv === 10 || dv === 11 ? 1 : dv
}
const bankBar = (c: string) => mod11(c.slice(0, 4) + c.slice(5), false) === Number(c[4])
const convenioTen = (c: string) => c[2] === '6' || c[2] === '7'

/** Dígitos verificadores de boleto (Febraban): barras de 44, linha digitável de 47 (banco) e de 48 (convênio). Só dígitos. */
export function boletoValid(code: string): boolean {
  if (!/^(\d{44}|\d{47}|\d{48})$/.test(code) || new Set(code).size === 1) return false
  if (code.length === 47) {
    if (mod10(code.slice(0, 9)) !== Number(code[9]) || mod10(code.slice(10, 20)) !== Number(code[20]) || mod10(code.slice(21, 31)) !== Number(code[31])) return false
    return bankBar(code.slice(0, 4) + code.slice(32, 47) + code.slice(4, 9) + code.slice(10, 20) + code.slice(21, 31))
  }
  if (code.length === 48) {
    for (let i = 0; i < 48; i += 12) {
      const block = code.slice(i, i + 11)
      if ((convenioTen(code) ? mod10(block) : mod11(block, true)) !== Number(code[i + 11])) return false
    }
    return true
  }
  if (code[0] === '8') {
    const rest = code.slice(0, 3) + code.slice(4)
    return (convenioTen(code) ? mod10(rest) : mod11(rest, true)) === Number(code[3])
  }
  return bankBar(code)
}

/** Mesma política do servidor (PasswordPolicy): tamanho, não ser previsível e não conter o e-mail. */
export function passwordIssue(password: string, email?: string): string | null {
  if (password.length < 12 || password.length > 128) return 'Use de 12 a 128 caracteres'
  const lower = password.toLowerCase()
  if (isTooEasy(lower)) return 'Essa senha é fácil de adivinhar: evite sequências, teclas em fila e palavras como "senha"'
  const local = (email ?? '').trim().toLowerCase().split('@')[0]
  if (local.length >= 4 && lower.includes(local)) return 'A senha não pode conter o seu e-mail'
  return null
}
