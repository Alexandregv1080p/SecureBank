import { describe, expect, it } from 'vitest'
import { createIdempotency } from './idempotency'
import { formatBRL, parseAmount, subtractAmounts, addAmounts } from './money'
import { toE164BR } from './phone'

describe('parseAmount', () => {
  it('aceita formatos brasileiros e simples', () => {
    expect(parseAmount('1.234,56')).toBe('1234.56')
    expect(parseAmount('1234.56')).toBe('1234.56')
    expect(parseAmount('10')).toBe('10')
    expect(parseAmount('R$ 0,5')).toBe('0.5')
    expect(parseAmount('1.500')).toBe('1500') // padrão brasileiro: mil e quinhentos
    expect(parseAmount('1.500.000,50')).toBe('1500000.50')
    expect(parseAmount('1.5')).toBe('1.5')
    expect(parseAmount('1.50')).toBe('1.50')
  })

  it('recusa zero, negativo, casas demais e lixo', () => {
    for (const bad of ['', '0', '0,00', '-5', '10,001', 'abc', '1,2,3', '12345678901234', '0.500', '1.5000', '1.23,4', '1,234.56']) {
      expect(parseAmount(bad)).toBeNull()
    }
  })
})

describe('formatBRL', () => {
  it('formata em reais', () => {
    expect(formatBRL('1234.5').replace(/\s/g, ' ')).toBe('R$ 1.234,50')
  })
})

describe('toE164BR', () => {
  it('normaliza telefones brasileiros', () => {
    expect(toE164BR('11 99999-8888')).toBe('+5511999998888')
    expect(toE164BR('+55 (11) 99999-8888')).toBe('+5511999998888')
    expect(toE164BR('1199')).toBeNull()
    expect(toE164BR('11 3333-4444')).toBe('+551133334444') // fixo
    expect(toE164BR('00 99999-8888')).toBeNull() // DDD que não existe
    expect(toE164BR('11 12345-6789')).toBeNull() // celular precisa começar com 9
    expect(toE164BR('11 93333-4444')).toBe('+5511933334444')
    expect(toE164BR('11 93333-444')).toBeNull() // fixo não começa com 9
  })
})

describe('createIdempotency', () => {
  it('reaproveita a chave para o mesmo pedido e troca quando o pedido muda ou o resultado é definitivo', () => {
    let n = 0
    const idem = createIdempotency(() => `key-${++n}`)

    const first = idem.keyFor({ amount: '10.00' })
    expect(idem.keyFor({ amount: '10.00' })).toBe(first) // retry após erro incerto
    expect(idem.keyFor({ amount: '20.00' })).not.toBe(first) // outro pedido, outra chave

    const current = idem.keyFor({ amount: '20.00' })
    idem.settle()
    expect(idem.keyFor({ amount: '20.00' })).not.toBe(current) // resultado definitivo: intenção nova
  })
})

describe('subtractAmounts', () => {
  it('subtrai em centavos, sem erro de ponto flutuante', () => {
    expect(subtractAmounts('1000.00', '250.50')).toBe('749.50')
    expect(subtractAmounts('0.30', '0.10')).toBe('0.20')
    expect(subtractAmounts('10.00', '10.00')).toBe('0.00')
  })
})

describe('addAmounts', () => {
  it('soma em centavos, sem erro de ponto flutuante', () => {
    expect(addAmounts('0.10', '0.20')).toBe('0.30')
    expect(addAmounts('1000.00', '250.50')).toBe('1250.50')
  })
})
