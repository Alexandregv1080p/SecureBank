import { describe, expect, it } from 'vitest'
import { applyError, lockedMessage, rateLabel, redeemError, taxBrackets, termLabel } from './investment'

describe('aplicar', () => {
  it('aceita do mínimo do produto até o saldo da conta', () => {
    expect(applyError('100,00', '100.00')).toBeNull()
    expect(applyError('2.500,00', '100.00', '3000.00')).toBeNull()
    expect(applyError('3.000,00', '100.00', '3000.00')).toBeNull()
  })

  it('recusa abaixo do mínimo, acima do saldo e valor inválido', () => {
    expect(applyError('99,99', '100.00')).toMatch(/mínimo/)
    expect(applyError('0,50', '1.00')).toMatch(/mínimo/)
    expect(applyError('3.000,01', '100.00', '3000.00')).toMatch(/saldo/)
    expect(applyError('0', '1.00')).toMatch(/maior que zero/)
    expect(applyError('abc', '1.00')).toMatch(/Use só números/)
    expect(applyError('', '1.00')).toMatch(/Informe o valor/)
  })
})

describe('resgatar', () => {
  it('em branco resgata tudo; com valor, ele precisa ser válido', () => {
    expect(redeemError('')).toBeNull()
    expect(redeemError('   ')).toBeNull()
    expect(redeemError('300,00')).toBeNull()
    expect(redeemError('0')).not.toBeNull()
    expect(redeemError('-3')).not.toBeNull()
    expect(redeemError('1,234')).not.toBeNull()
  })
})

describe('textos', () => {
  it('descreve prazo e taxa', () => {
    expect(termLabel(null)).toBe('Liquidez diária')
    expect(termLabel(90)).toBe('Prazo de 90 dias')
    expect(rateLabel('10.50')).toBe('10,50% ao ano')
  })

  it('a tabela de IR é regressiva e fecha em 15%', () => {
    expect(taxBrackets.map((b) => b.rate)).toEqual(['22,5%', '20%', '17,5%', '15%'])
    expect(taxBrackets.map((b) => b.upTo)).toEqual([180, 360, 720, Infinity])
  })

  it('só explica o bloqueio quando há vencimento', () => {
    expect(lockedMessage(null, (s) => s)).toBeNull()
    expect(lockedMessage('2026-12-30', (s) => `em ${s}`)).toBe('Este investimento só pode ser resgatado no vencimento: em 2026-12-30.')
  })
})
