import { describe, expect, it } from 'vitest'
import { formatCardNumber, formatExpiry, merchantIssue, purchaseIssues } from './card'

const limit = { perOperation: { amount: '3000.00', currency: 'BRL' }, remainingToday: { amount: '1000.00', currency: 'BRL' } }

describe('cartão', () => {
  it('agrupa o número de 4 em 4 e mostra a validade com dois dígitos', () => {
    expect(formatCardNumber('9123456789012345')).toBe('9123 4567 8901 2345')
    expect(formatCardNumber('9123 4567')).toBe('9123 4567')
    expect(formatExpiry(7, 2031)).toBe('07/31')
    expect(formatExpiry(12, 2030)).toBe('12/30')
  })

  it('o estabelecimento é obrigatório, curto e sem caracteres de controle', () => {
    expect(merchantIssue('Mercado Central')).toBeNull()
    expect(merchantIssue('Café & Cia 🍕')).toBeNull()
    expect(merchantIssue('   ')).toBe('Informe o estabelecimento')
    expect(merchantIssue('x'.repeat(61))).toBe('No máximo 60 caracteres')
    expect(merchantIssue('Loja\nlinha 2')).not.toBeNull()
    expect(merchantIssue('Loja\u202eevil')).not.toBeNull()
  })

  it('a compra confere valor, saldo e limite antes de enviar', () => {
    expect(purchaseIssues('50,00', 'Padaria', '100.00', limit)).toEqual({})
    expect(purchaseIssues('', 'Padaria', '100.00', limit).amount).toBe('Informe o valor')
    expect(purchaseIssues('100,01', 'Padaria', '100.00', limit).amount).toMatch(/^Saldo insuficiente/)
    expect(purchaseIssues('3.000,01', 'Padaria', '9999.00', limit).amount).toMatch(/^Acima do limite por operação/)
    expect(purchaseIssues('1.000,01', 'Padaria', '9999.00', limit).amount).toMatch(/^Acima do que resta/)
    expect(purchaseIssues('10', ' ', '100.00', limit).merchant).toBe('Informe o estabelecimento')
  })
})
