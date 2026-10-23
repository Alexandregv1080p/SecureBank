import { describe, expect, it } from 'vitest'
import { creditPurchaseIssues, dueHint, holderIssue, installmentAmounts, installmentOptions, invoiceStatusLabel, limitIssue, monthLabel, payIssue, shortDate } from './credit'

describe('crédito', () => {
  it('escreve o mês e as datas da fatura em português', () => {
    expect(monthLabel('2026-11')).toBe('novembro de 2026')
    expect(monthLabel('2027-01')).toBe('janeiro de 2027')
    expect(shortDate('2026-11-10')).toBe('10/11')
  })

  it('divide em centavos inteiros e a última parcela leva o resto (igual ao servidor)', () => {
    expect(installmentAmounts(10000, 3)).toEqual([3333, 3333, 3334])
    expect(installmentAmounts(10000, 1)).toEqual([10000])
    expect(installmentAmounts(10, 3).reduce((a, b) => a + b, 0)).toBe(10)
  })

  it('só oferece parcelas que respeitam o mínimo de R$ 5,00', () => {
    const labels = (v: string) => installmentOptions(v).map((o) => o.n)
    expect(labels('')).toEqual([1])
    expect(labels('20,00')).toEqual([1, 2, 3, 4]) // 5x seria R$ 4,00
    expect(labels('25')).toEqual([1, 2, 3, 4, 5])
    expect(labels('1.000,00')).toHaveLength(12)
    expect(installmentOptions('100,00')[2].label.replace(/\s/g, ' ')).toBe('3x de R$ 33,33 (última R$ 33,34), sem juros')
  })

  it('a compra confere valor, limite disponível e parcela mínima', () => {
    expect(creditPurchaseIssues('500,00', 'Loja', 3, '2000.00')).toEqual({})
    expect(creditPurchaseIssues('2.000,01', 'Loja', 1, '2000.00').amount).toMatch(/^Passa do limite disponível/)
    expect(creditPurchaseIssues('20,00', 'Loja', 5, '2000.00').amount).toMatch(/parcela mínima/)
    expect(creditPurchaseIssues('', 'Loja', 1).amount).toBe('Informe o valor')
    expect(creditPurchaseIssues('10', '  ', 1).merchant).toBe('Informe o estabelecimento')
  })

  it('pagar a fatura vai de 1 centavo até o que falta', () => {
    expect(payIssue('150,00', '250.00')).toBeNull()
    expect(payIssue('250,00', '250.00')).toBeNull()
    expect(payIssue('250,01', '250.00')).toMatch(/passa do que falta/)
    expect(payIssue('', '250.00')).toBe('Informe o valor')
    expect(payIssue('0', '250.00')).not.toBeNull()
  })

  it('diz o que fazer conforme a situação da fatura', () => {
    const base = { closingDate: '2026-11-03', dueDate: '2026-11-10' }
    expect(dueHint({ ...base, status: 'OPEN' })).toBe('Fecha em 03/11 e vence em 10/11')
    expect(dueHint({ ...base, status: 'CLOSED' })).toBe('Fechada: vence em 10/11')
    expect(dueHint({ ...base, status: 'OVERDUE' })).toMatch(/^Venceu em 10\/11/)
    expect(dueHint({ ...base, status: 'PAID' })).toBe('Fatura paga')
  })
})

describe('crédito: limite e adicional', () => {
  it('confere o novo limite', () => {
    expect(limitIssue('3.000,00', '500.00')).toBeNull()
    expect(limitIssue('3.050,00', '0.00')).toMatch(/múltiplo/)
    expect(limitIssue('10.100,00', '0.00')).toMatch(/equipe/)
    expect(limitIssue('500,00', '800.00')).toMatch(/abaixo do que já está em uso/)
    expect(limitIssue('', '0.00')).not.toBeNull()
  })

  it('o rotativo tem rótulo e dica próprios', () => {
    expect(invoiceStatusLabel.REVOLVING).toBe('No rotativo')
    expect(dueHint({ status: 'REVOLVING', dueDate: '2026-11-10', closingDate: '2026-11-03' })).toMatch(/juros/)
  })

  it('o nome do adicional vai de 2 a 40 caracteres', () => {
    expect(holderIssue('Maria')).toBeNull()
    expect(holderIssue(' a ')).not.toBeNull()
    expect(holderIssue('x'.repeat(41))).not.toBeNull()
  })
})
