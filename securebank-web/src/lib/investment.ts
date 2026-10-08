import { formatBRL, parseAmount } from './money'

/** Valor da aplicação: positivo, com até 2 casas, e pelo menos o mínimo do produto (o servidor confere de novo). */
export function applyError(input: string, minAmount: string, available?: string): string | null {
  const parsed = parseAmount(input)
  if (parsed === null) return 'Informe um valor maior que zero, com até 2 casas decimais'
  const cents = Math.round(Number(parsed) * 100)
  if (cents < Math.round(Number(minAmount) * 100)) return `O mínimo deste produto é ${formatBRL(minAmount)}`
  if (available !== undefined && cents > Math.round(Number(available) * 100)) return 'O valor passa do saldo da conta'
  return null
}

/** Resgate: em branco resgata tudo; com valor, resgata esse LÍQUIDO (positivo, até 2 casas). */
export function redeemError(input: string): string | null {
  if (!input.trim()) return null
  return parseAmount(input) === null ? 'Informe um valor maior que zero, com até 2 casas decimais' : null
}

export const termLabel = (termDays: number | null) => (termDays === null ? 'Liquidez diária' : `Prazo de ${termDays} dias`)

/** "10.50" → "10,50% ao ano". */
export const rateLabel = (percent: string) => `${percent.replace('.', ',')}% ao ano`

/** Tabela regressiva do IR sobre o rendimento (a mesma do servidor; o valor mostrado vem dele, isto é só o texto explicativo). */
export const taxBrackets = [
  { upTo: 180, rate: '22,5%' },
  { upTo: 360, rate: '20%' },
  { upTo: 720, rate: '17,5%' },
  { upTo: Infinity, rate: '15%' },
]

export const taxExplanation =
  'IR regressivo sobre o rendimento: 22,5% até 180 dias, 20% até 360, 17,5% até 720 e 15% acima. O rendimento conta por dia completo, com juros compostos.'

/** Por que ainda não dá para resgatar (produto com prazo): a data do vencimento. */
export function lockedMessage(maturesAt: string | null, format: (iso: string) => string): string | null {
  return maturesAt ? `Este investimento só pode ser resgatado no vencimento: ${format(maturesAt)}.` : null
}
