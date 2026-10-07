import { describe, expect, it } from 'vitest'
import { auditEventLabel, canAccess, isAlertEvent, isStaff, isUuid, parseRate, parseSpread, roleLabel, sectionsFor, shortId, staffSections } from './staff'

describe('seções do painel por papel', () => {
  it('o administrador vê todas as seções', () => {
    expect(sectionsFor(['ADMIN'])).toHaveLength(staffSections.length)
  })

  it('o suporte vê só auditoria e clientes', () => {
    expect(sectionsFor(['SUPPORT']).map((s) => s.to)).toEqual(['/equipe/auditoria', '/equipe/clientes'])
    expect(canAccess(['SUPPORT'], '/equipe/cambio')).toBe(false)
    expect(canAccess(['SUPPORT'], '/equipe/auditoria')).toBe(true)
  })

  it('cliente não vê nada e não é equipe', () => {
    expect(sectionsFor(['CUSTOMER'])).toEqual([])
    expect(isStaff(['CUSTOMER'])).toBe(false)
    expect(isStaff([])).toBe(false)
    expect(isStaff(['SUPPORT'])).toBe(true)
    expect(isStaff(['ADMIN'])).toBe(true)
  })

  it('toda seção tem caminho único dentro de /equipe', () => {
    const paths = staffSections.map((s) => s.to)
    expect(new Set(paths).size).toBe(paths.length)
    expect(paths.every((p) => p.startsWith('/equipe/'))).toBe(true)
  })
})

describe('textos da auditoria', () => {
  it('traduz eventos conhecidos e mantém os desconhecidos', () => {
    expect(auditEventLabel('PIX_SENT')).toBe('Pix enviado')
    expect(auditEventLabel('FX_RATE_CHANGED')).toBe('Cotação alterada')
    expect(auditEventLabel('ALGO_NOVO')).toBe('ALGO_NOVO')
  })

  it('destaca recusas e eventos sensíveis, mas não o desbloqueio', () => {
    expect(isAlertEvent('LOGIN_FAILED')).toBe(true)
    expect(isAlertEvent('ACCESS_DENIED')).toBe(true)
    expect(isAlertEvent('ACCOUNT_BLOCKED')).toBe(true)
    expect(isAlertEvent('REFRESH_TOKEN_REUSE_DETECTED')).toBe(true)
    expect(isAlertEvent('ACCOUNT_UNBLOCKED')).toBe(false)
    expect(isAlertEvent('PIX_SENT')).toBe(false)
  })

  it('formata papéis e ids', () => {
    expect(roleLabel('ADMIN')).toBe('Administrador')
    expect(roleLabel('SUPPORT')).toBe('Suporte')
    expect(roleLabel('X')).toBe('X')
    expect(shortId('0e9b9c1a-1111-2222-3333-444455556666')).toBe('0e9b9c1a')
    expect(shortId(null)).toBe('—')
  })

  it('reconhece UUID', () => {
    expect(isUuid('0e9b9c1a-1111-2222-3333-444455556666')).toBe(true)
    expect(isUuid(' 0E9B9C1A-1111-2222-3333-444455556666 ')).toBe(true)
    expect(isUuid('123')).toBe(false)
    expect(isUuid('')).toBe(false)
  })
})

describe('valores da tela de câmbio', () => {
  it('aceita cotação positiva com vírgula ou ponto', () => {
    expect(parseRate('5,2')).toBe('5.2')
    expect(parseRate(' 5.278000 ')).toBe('5.278000')
    expect(parseRate('0')).toBeNull()
    expect(parseRate('-1')).toBeNull()
    expect(parseRate('abc')).toBeNull()
    expect(parseRate('')).toBeNull()
    expect(parseRate('5,1234567')).toBeNull()
  })

  it('aceita spread de 0 a 10 com até 2 casas', () => {
    expect(parseSpread('1,5')).toBe('1.5')
    expect(parseSpread('0')).toBe('0')
    expect(parseSpread('10')).toBe('10')
    expect(parseSpread('10,01')).toBeNull()
    expect(parseSpread('11')).toBeNull()
    expect(parseSpread('-1')).toBeNull()
    expect(parseSpread('1,555')).toBeNull()
  })
})
