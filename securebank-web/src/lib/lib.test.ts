import { describe, expect, it } from 'vitest'
import { createIdempotency } from './idempotency'
import { formatBRL, parseAmount } from './money'
import { toE164BR } from './phone'

describe('parseAmount', () => {
  it('aceita formatos brasileiros e simples', () => {
    expect(parseAmount('1.234,56')).toBe('1234.56')
    expect(parseAmount('1234.56')).toBe('1234.56')
    expect(parseAmount('10')).toBe('10')
    expect(parseAmount('R$ 0,5')).toBe('0.5')
  })

  it('recusa zero, negativo, casas demais e lixo', () => {
    for (const bad of ['', '0', '0,00', '-5', '10,001', 'abc', '1,2,3', '12345678901234']) {
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
