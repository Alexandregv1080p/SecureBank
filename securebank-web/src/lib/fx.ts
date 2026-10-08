import { formatBRL, parseAmount } from './money'

export const currencyName = (code: string) => ({ USD: 'Dólar americano', EUR: 'Euro' })[code] ?? code
export const currencySymbol = (code: string) => ({ USD: 'US$', EUR: '€' })[code] ?? code

/** "1234.50" em dólar → "US$ 1.234,50". */
export function formatForeign(code: string, amount: string): string {
  return `${currencySymbol(code)} ${formatBRL(amount).replace(/^R\$\s?/, '')}`
}

/** "5.278000" → "5,278". Até quatro casas (metade para cima, em inteiros: o toFixed erra em 5,56525), sem zeros sobrando e sempre com duas. */
export function rateLabel(rate: string): string {
  const m = micros(rate)
  if (m === null) return rate
  const rounded = (m + 50n) / 100n
  const int = rounded / 10_000n
  const decimals = (rounded % 10_000n).toString().padStart(4, '0').replace(/0+$/, '')
  return `${int},${decimals.padEnd(2, '0')}`
}

/** Cotação (reais por unidade) como inteiro em milionésimos, sem passar por ponto flutuante. */
function micros(rate: string): bigint | null {
  const m = /^(\d{1,6})(?:\.(\d{1,6}))?$/.exec(rate.trim())
  return m ? BigInt(m[1]) * 1_000_000n + BigInt((m[2] ?? '').padEnd(6, '0')) : null
}

const cents = (amount: string) => BigInt(Math.round(Number(amount) * 100))

function toReais(valueCents: bigint): string {
  const s = valueCents.toString().padStart(3, '0')
  return `${s.slice(0, -2)}.${s.slice(-2)}`
}

/**
 * Estimativas iguais às do servidor, em inteiros: a COMPRA arredonda o custo para cima e a VENDA arredonda o recebido
 * para baixo (sempre a favor do banco). O servidor é quem vale; isto só mostra o número antes de enviar.
 */
export function buyCost(amount: string, buyRate: string): string | null {
  const value = parseAmount(amount)
  const rate = micros(buyRate)
  if (value === null || rate === null) return null
  const product = cents(value) * rate
  return toReais((product + 999_999n) / 1_000_000n)
}

export function sellProceeds(amount: string, sellRate: string): string | null {
  const value = parseAmount(amount)
  const rate = micros(sellRate)
  if (value === null || rate === null) return null
  return toReais((cents(value) * rate) / 1_000_000n)
}

/** Quantia positiva com até 2 casas; na venda, também não pode passar do que há na carteira. */
export function amountError(input: string, available?: string): string | null {
  const parsed = parseAmount(input)
  if (parsed === null) return 'Informe um valor maior que zero, com até 2 casas decimais'
  if (available !== undefined && cents(parsed) > cents(available)) return `Você tem só ${available.replace('.', ',')} na carteira`
  return null
}

export const sideLabel = (side: string) => (side === 'BUY' ? 'Compra' : 'Venda')
