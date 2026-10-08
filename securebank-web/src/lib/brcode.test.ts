import { describe, expect, it } from 'vitest'
import { InvalidBrCodeError, chargeTxid, crc16, decode, encode, encodeDynamic, looksLikeCode } from './brcode'

const TXID = 'AbCdEfGhIjKlMnOpQrStUvWxYz012345'

describe('crc16', () => {
  it('bate com o valor de referência do CRC-16/CCITT-FALSE', () => {
    expect(crc16('123456789')).toBe('29B1')
    expect(crc16('')).toBe('FFFF')
  })
})

describe('código estático', () => {
  it('gera e lê de volta: chave, valor e nome sem acento', () => {
    const code = encode('ana@example.com', 'José da Silva Ñandú', { amount: '10.50' })
    const data = decode(code)

    expect(code.startsWith('000201')).toBe(true)
    expect(data.key).toBe('ana@example.com')
    expect(data.location).toBeNull()
    expect(data.amount).toBe('10.50')
    expect(data.name).toBe('JOSE DA SILVA NANDU')
    expect(data.city).toBe('SAO PAULO')
    expect(crc16(code.slice(0, -4))).toBe(code.slice(-4))
  })

  it('sem valor deixa quem paga digitar', () => {
    expect(decode(encode('+5511987654321', 'Ana')).amount).toBeNull()
  })

  it('nome vazio ou só de símbolos vira RECEBEDOR e o nome é cortado em 25', () => {
    expect(decode(encode('a@b.co', '***')).name).toBe('RECEBEDOR')
    expect(decode(encode('a@b.co', 'x'.repeat(40))).name).toHaveLength(25)
  })

  it('recusa chave vazia ou grande demais', () => {
    expect(() => encode('  ', 'Ana')).toThrow()
    expect(() => encode('a'.repeat(78), 'Ana')).toThrow()
  })
})

describe('código dinâmico (cobrança)', () => {
  it('leva o endereço no lugar da chave e marca uso único', () => {
    const code = encodeDynamic(`pix.securebank.example/charges/${TXID}`, 'Ana Souza')
    const data = decode(code)

    expect(data.key).toBeNull()
    expect(data.location).toBe(`pix.securebank.example/charges/${TXID}`)
    expect(data.name).toBe('ANA SOUZA')
    expect(code).toContain('010212')
  })

  it('o txid só sai do endereço no formato esperado', () => {
    expect(chargeTxid(`pix.securebank.example/charges/${TXID}`)).toBe(TXID)
    expect(chargeTxid(`https://pix.exemplo.com/v2/charges/${TXID}/`)).toBe(TXID)
    expect(chargeTxid('pix.securebank.example/charges/curto')).toBeNull()
    expect(chargeTxid(`pix.securebank.example/outra/${TXID}`)).toBeNull()
    expect(chargeTxid(`pix.securebank.example/charges/${TXID}/../../etc`)).toBeNull()
    expect(chargeTxid('')).toBeNull()
  })
})

describe('leitura de código ruim', () => {
  const good = encode('ana@example.com', 'Ana')

  it('pega um caractere trocado pelo CRC', () => {
    const broken = good.replace('ana@', 'anb@')
    expect(() => decode(broken)).toThrow(InvalidBrCodeError)
    expect(() => decode(broken)).toThrow(/corrompido/)
  })

  it('recusa o que não é código, ou está cortado', () => {
    expect(() => decode('ana@example.com')).toThrow(/Não parece/)
    expect(() => decode('')).toThrow(InvalidBrCodeError)
    expect(() => decode(good.slice(0, -10))).toThrow(InvalidBrCodeError)
  })

  it('recusa código sem chave nem endereço', () => {
    const body = '000201' + '26' + '18' + '0014br.gov.bcb.pix' + '5204000053039865802BR5903ANA6003SAO' + '6304'
    expect(() => decode(body + crc16(body))).toThrow(/chave/)
  })

  it('reconhece um copia-e-cola e não confunde com chave', () => {
    expect(looksLikeCode(good)).toBe(true)
    expect(looksLikeCode(`  ${good}`)).toBe(true)
    expect(looksLikeCode('ana@example.com')).toBe(false)
    expect(looksLikeCode('12345678909')).toBe(false)
  })
})
