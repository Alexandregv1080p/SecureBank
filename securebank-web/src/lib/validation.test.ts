import { describe, expect, it } from 'vitest'
import {
  accountNumberValid, amountIssue, maskAccountNumber, balanceIssue, cpfValid, fullNameIssue, limitIssue, maskCpf, maskPhone, passwordIssue,
  sameAccount, sanitizeAmount,
} from './validation'

const usage = (perOperation: string, remainingToday: string) => ({
  perOperation: { amount: perOperation, currency: 'BRL' },
  remainingToday: { amount: remainingToday, currency: 'BRL' },
})

describe('validation (espelha o Kotlin e o servidor)', () => {
  it('o dígito da conta é o módulo 11 do servidor', () => {
    for (const ok of ['100048-9', '100049-7', '100051-9', '000001-9']) expect(accountNumberValid(ok), ok).toBe(true)
    for (const bad of ['100048-8', '100049-0', '100051-1', '12345-6', '1000489', 'abc123-4', '']) expect(accountNumberValid(bad), bad).toBe(false)
  })

  it('mesma conta só com agência e número iguais', () => {
    const src = { branch: '0001', accountNumber: '100048-9' }
    expect(sameAccount(src, '0001', '100048-9')).toBe(true)
    expect(sameAccount(src, '0002', '100048-9')).toBe(false)
    expect(sameAccount(src, '0001', '100049-7')).toBe(false)
  })

  it('digitar valor mantém só reais e centavos', () => {
    expect(sanitizeAmount('')).toBe('')
    expect(sanitizeAmount('abc')).toBe('')
    expect(sanitizeAmount('1250,5')).toBe('1250,5')
    expect(sanitizeAmount('12,345')).toBe('12,34')
    expect(sanitizeAmount('1.234,56')).toBe('1234,56')
    expect(sanitizeAmount('1.5')).toBe('1,5')
    expect(sanitizeAmount('007')).toBe('7')
    expect(sanitizeAmount(',5')).toBe('0,5')
    expect(sanitizeAmount('0')).toBe('0')
    expect(sanitizeAmount('1,,2')).toBe('1,2')
    expect(sanitizeAmount('12345678901234567')).toHaveLength(13)
  })

  it('valor inválido diz por quê', () => {
    expect(amountIssue('1.234,56')).toBeNull()
    expect(amountIssue('10')).toBeNull()
    expect(amountIssue('')).toBe('Informe o valor')
    expect(amountIssue('  ')).toBe('Informe o valor')
    expect(amountIssue('0,00')).toBe('O valor deve ser maior que zero')
    expect(amountIssue('1,234')).toBe('Use no máximo 2 casas decimais')
    expect(amountIssue('1.500')).toBe('Use no máximo 2 casas decimais')
    expect(amountIssue('12345678901234')).toBe('Esse valor é alto demais')
    expect(amountIssue('1,2,3')).not.toBeNull()
    expect(amountIssue('abc')).not.toBeNull()
  })

  it('compara com saldo e limites em centavos', () => {
    expect(balanceIssue('100.00', '100.00')).toBeNull()
    expect(balanceIssue('100.01', '100.00')).toMatch(/^Saldo insuficiente/)
    expect(balanceIssue('0.30', '0.30')).toBeNull() // sem erro de ponto flutuante
    expect(limitIssue('100.00', null)).toBeNull()
    expect(limitIssue('1000.00', usage('1000.00', '1000.00'))).toBeNull()
    expect(limitIssue('1000.01', usage('1000.00', '5000.00'))).toMatch(/^Acima do limite por operação/)
    expect(limitIssue('600.00', usage('1000.00', '500.00'))).toMatch(/^Acima do que resta/)
  })

  it('CPF precisa de dígitos verificadores válidos', () => {
    expect(cpfValid('529.982.247-25')).toBe(true)
    expect(cpfValid('52998224725')).toBe(true)
    expect(cpfValid('529.982.247-24')).toBe(false)
    expect(cpfValid('111.111.111-11')).toBe(false)
    expect(cpfValid('123')).toBe(false)
  })

  it('máscaras seguem os dígitos', () => {
    expect(maskCpf('')).toBe('')
    expect(maskCpf('52998')).toBe('529.98')
    expect(maskCpf('52998224725999')).toBe('529.982.247-25')
    expect(maskPhone('')).toBe('')
    expect(maskPhone('1')).toBe('(1')
    expect(maskPhone('11')).toBe('(11)')
    expect(maskPhone('119999')).toBe('(11) 9999')
    expect(maskPhone('1199998888')).toBe('(11) 9999-8888')
    expect(maskPhone('11999998888')).toBe('(11) 99999-8888')
    expect(maskPhone('+55 11 99999-8888')).toBe('(11) 99999-8888')
  })

  it('o hífen da conta entra antes do dígito verificador', () => {
    expect(maskAccountNumber('')).toBe('')
    expect(maskAccountNumber('12345')).toBe('12345')
    expect(maskAccountNumber('123456')).toBe('123456')
    expect(maskAccountNumber('1234560')).toBe('123456-0')
    expect(maskAccountNumber('12345678')).toBe('1234567-8')
    expect(maskAccountNumber('123456-0')).toBe('123456-0')
    expect(maskAccountNumber('1234567890123456')).toBe('123456789012-3')
  })

  it('o nome precisa de sobrenome e só letras', () => {
    expect(fullNameIssue('Maria da Silva')).toBeNull()
    expect(fullNameIssue("Ana D'Ávila-Souza")).toBeNull()
    expect(fullNameIssue('Maria')).toBe('Informe nome e sobrenome')
    expect(fullNameIssue('  ')).toBe('Informe nome e sobrenome')
    expect(fullNameIssue('Maria 123')).toBe('Use só letras no nome')
  })

  it('a senha segue a política do servidor', () => {
    expect(passwordIssue('Correct-Horse-Battery-9', 'marina@example.com')).toBeNull()
    expect(passwordIssue('curta')).toBe('Use de 12 a 128 caracteres')
    expect(passwordIssue('a'.repeat(129))).toBe('Use de 12 a 128 caracteres')
    expect(passwordIssue('senha1234567')).toBe('Essa senha é fácil de adivinhar')
    expect(passwordIssue('aaaabbbbaaaa')).toBe('Essa senha é fácil de adivinhar')
    expect(passwordIssue('xx-marina-xx-2026', 'Marina@example.com')).toBe('A senha não pode conter o seu e-mail')
    expect(passwordIssue('xx-ana-xx-2026-ok', 'ana@example.com')).toBeNull()
  })
})
