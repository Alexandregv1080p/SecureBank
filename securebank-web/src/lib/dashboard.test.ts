import { describe, expect, it } from 'vitest'
import type { StatementSummary } from '../services/types'
import { combineMonths, compactBRL, deltaPercent, expenseShares, initials, lastMonths, monthShort, niceMax, titleCase } from './dashboard'

const money = (amount: string) => ({ amount, currency: 'BRL' })

function summary(month: string, income: string, expenses: string, byCategory: [string, string, string][] = []): StatementSummary {
  return {
    month,
    income: money(income),
    expenses: money(expenses),
    net: money('0.00'),
    byCategory: byCategory.map(([category, i, e]) => ({ category, income: money(i), expenses: money(e) })),
  }
}

describe('meses', () => {
  it('lista os últimos meses do mais antigo ao atual, atravessando o ano', () => {
    expect(lastMonths(4, new Date(2026, 0, 15))).toEqual(['2025-10', '2025-11', '2025-12', '2026-01'])
    expect(lastMonths(1, new Date(2026, 9, 31))).toEqual(['2026-10'])
    expect(lastMonths(6, new Date(2026, 9, 7))[0]).toBe('2026-05')
  })

  it('abrevia o mês em português', () => {
    expect(monthShort('2026-10')).toBe('out')
    expect(monthShort('2026-01')).toBe('jan')
    expect(monthShort('2026-12')).toBe('dez')
  })
})

describe('totais por mês', () => {
  it('soma as contas em centavos, sem erro de ponto flutuante', () => {
    const months = ['2026-09', '2026-10']
    const totals = combineMonths(months, [
      [summary('2026-09', '0.10', '0.20'), summary('2026-09', '0.20', '0.10')],
      [summary('2026-10', '1000.00', '250.50'), undefined],
    ])

    expect(totals[0]).toEqual({ month: '2026-09', income: 0.3, expenses: 0.3 })
    expect(totals[1]).toEqual({ month: '2026-10', income: 1000, expenses: 250.5 })
  })

  it('mês sem dados vale zero', () => {
    expect(combineMonths(['2026-10'], [])).toEqual([{ month: '2026-10', income: 0, expenses: 0 }])
  })
})

describe('variação', () => {
  it('compara com o mês anterior e não inventa base', () => {
    expect(deltaPercent(150, 100)).toBe(50)
    expect(deltaPercent(50, 100)).toBe(-50)
    expect(deltaPercent(100, 0)).toBeNull()
    expect(deltaPercent(0, 0)).toBeNull()
  })
})

describe('saídas por categoria', () => {
  it('soma as contas, ordena da maior para a menor e calcula o percentual', () => {
    const shares = expenseShares([
      summary('2026-10', '0', '0', [['PIX', '0', '300.00'], ['CASH', '500.00', '0.00'], ['FX', '0', '100.00']]),
      summary('2026-10', '0', '0', [['PIX', '0', '100.00'], ['PAYMENTS', '0', '500.00']]),
    ])

    expect(shares.map((s) => s.category)).toEqual(['PAYMENTS', 'PIX', 'FX'])
    expect(shares[1].value).toBe(400)
    expect(shares.reduce((a, s) => a + s.percent, 0)).toBeCloseTo(100, 6)
    expect(shares[0].percent).toBeCloseTo(50, 6)
    expect(shares[0].label).toBe('Pagamentos')
  })

  it('sem nenhuma saída não há fatias', () => {
    expect(expenseShares([summary('2026-10', '10', '0', [['CASH', '10.00', '0.00']])])).toEqual([])
    expect(expenseShares([undefined])).toEqual([])
  })
})

describe('nomes e valores curtos', () => {
  it('arruma a caixa do nome como foi digitado', () => {
    expect(titleCase('TEste')).toBe('Teste')
    expect(titleCase('maria DA silva')).toBe('Maria da Silva')
    expect(titleCase('  ana   souza ')).toBe('Ana Souza')
    expect(initials('maria da silva')).toBe('MS')
    expect(initials('Ana')).toBe('A')
    expect(initials('')).toBe('?')
  })

  it('abrevia valores do eixo', () => {
    expect(compactBRL(0)).toBe('0')
    expect(compactBRL(850)).toBe('850')
    expect(compactBRL(1000)).toBe('1 mil')
    expect(compactBRL(1250)).toBe('1,3 mil')
    expect(compactBRL(2_500_000)).toBe('2,5 mi')
  })

  it('escolhe um teto redondo para o eixo', () => {
    expect(niceMax(0)).toBe(1)
    expect(niceMax(730)).toBe(1000)
    expect(niceMax(1840)).toBe(2000)
    expect(niceMax(2300)).toBe(2500)
    expect(niceMax(4200)).toBe(5000)
    expect(niceMax(5100)).toBe(10000)
    expect(niceMax(100)).toBe(100)
  })
})
