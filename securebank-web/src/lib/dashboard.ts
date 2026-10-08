import type { StatementSummary } from '../services/types'

/** Meses 'AAAA-MM' dos últimos [count] (do mais antigo ao atual), pelo calendário local. */
export function lastMonths(count: number, now = new Date()): string[] {
  const months: string[] = []
  for (let i = count - 1; i >= 0; i--) {
    const d = new Date(now.getFullYear(), now.getMonth() - i, 1)
    months.push(`${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`)
  }
  return months
}

const shortMonths = ['jan', 'fev', 'mar', 'abr', 'mai', 'jun', 'jul', 'ago', 'set', 'out', 'nov', 'dez']

export const monthShort = (ym: string) => shortMonths[Number(ym.slice(5, 7)) - 1] ?? ym

const cents = (amount: string) => Math.round(Number(amount) * 100)

export interface MonthTotals {
  month: string
  income: number
  expenses: number
}

/** Soma as contas de cada mês (em reais, calculado em centavos inteiros). Mês sem resposta conta zero. */
export function combineMonths(months: string[], perMonth: (StatementSummary | undefined)[][]): MonthTotals[] {
  return months.map((month, i) => {
    const list = perMonth[i] ?? []
    const income = list.reduce((acc, s) => acc + (s ? cents(s.income.amount) : 0), 0)
    const expenses = list.reduce((acc, s) => acc + (s ? cents(s.expenses.amount) : 0), 0)
    return { month, income: income / 100, expenses: expenses / 100 }
  })
}

/** Variação percentual sobre o mês anterior; nulo quando não há base de comparação. */
export function deltaPercent(current: number, previous: number): number | null {
  if (!(previous > 0)) return null
  return ((current - previous) / previous) * 100
}

export const categoryMeta: Record<string, { label: string; color: string }> = {
  CASH: { label: 'Depósitos e saques', color: 'var(--chart-5)' },
  TRANSFERS: { label: 'Transferências', color: 'var(--chart-2)' },
  PAYMENTS: { label: 'Pagamentos', color: 'var(--chart-3)' },
  PIX: { label: 'Pix', color: 'var(--chart-1)' },
  SAVINGS: { label: 'Porquinhos', color: 'var(--chart-6)' },
  INVESTMENTS: { label: 'Investimentos', color: 'var(--chart-4)' },
  FX: { label: 'Câmbio', color: 'var(--chart-7)' },
}

export interface Share {
  category: string
  label: string
  color: string
  value: number
  percent: number
}

/** Saídas do mês por categoria, de todas as contas, da maior para a menor (só o que teve saída). */
export function expenseShares(summaries: (StatementSummary | undefined)[]): Share[] {
  const totals = new Map<string, number>()
  for (const s of summaries) {
    for (const c of s?.byCategory ?? []) {
      const value = cents(c.expenses.amount)
      if (value > 0) totals.set(c.category, (totals.get(c.category) ?? 0) + value)
    }
  }
  const sum = [...totals.values()].reduce((a, b) => a + b, 0)
  return [...totals.entries()]
    .sort((a, b) => b[1] - a[1])
    .map(([category, value]) => ({
      category,
      label: categoryMeta[category]?.label ?? category,
      color: categoryMeta[category]?.color ?? 'var(--chart-8)',
      value: value / 100,
      percent: sum ? (value / sum) * 100 : 0,
    }))
}

/** "TEste maria" → "Teste Maria": o cadastro guarda o nome como foi digitado. */
export function titleCase(name: string): string {
  return name
    .toLowerCase()
    .split(/\s+/)
    .filter(Boolean)
    .map((w) => (['de', 'da', 'do', 'das', 'dos', 'e'].includes(w) ? w : w[0].toUpperCase() + w.slice(1)))
    .join(' ')
}

export const initials = (name: string) =>
  titleCase(name)
    .split(' ')
    .filter((w) => w.length > 2 || w === titleCase(name))
    .slice(0, 2)
    .map((w) => w[0])
    .join('')
    .toUpperCase() || '?'

/** Valor curto para o eixo do gráfico: 1,2 mil / 3,4 mi. */
export function compactBRL(value: number): string {
  const abs = Math.abs(value)
  if (abs >= 1_000_000) return `${(value / 1_000_000).toFixed(1).replace('.', ',').replace(',0', '')} mi`
  if (abs >= 1_000) return `${(value / 1_000).toFixed(1).replace('.', ',').replace(',0', '')} mil`
  return String(Math.round(value))
}

/** Teto "redondo" do eixo Y (1, 2, 2,5, 5 ou 10 × potência de 10) para o gráfico não terminar em número estranho. */
export function niceMax(value: number): number {
  if (!(value > 0)) return 1
  const pow = Math.pow(10, Math.floor(Math.log10(value)))
  const n = value / pow
  const step = n <= 1 ? 1 : n <= 2 ? 2 : n <= 2.5 ? 2.5 : n <= 5 ? 5 : 10
  return step * pow
}
