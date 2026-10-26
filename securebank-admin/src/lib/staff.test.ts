import { describe, expect, it } from 'vitest'
import { reasonIssue, creditLimitIssue, auditEventLabel, can, canAccess, departmentLabel, departments, isAlertEvent, isStaff, isUuid, parseRate, parseSpread, positionLabel, roleLabel, searchError, accountStatusLabel, sectionsFor, shortId, staffSections, approvalKindLabel, approvalStatusLabel } from './staff'

describe('seções do painel por papel', () => {
  const paths = (role: string) => sectionsFor([role]).map((s) => s.to)

  it('a Diretoria vê todas as seções', () => {
    expect(sectionsFor(['ADMIN'])).toHaveLength(staffSections.length)
  })

  it('cada departamento vê só as telas do que faz, e todos veem a própria segurança', () => {
    expect(paths('SUPPORT')).toEqual(['/auditoria', '/clientes', '/seguranca'])
    expect(paths('RISK')).toEqual(['/aprovacoes', '/auditoria', '/clientes', '/contas', '/seguranca'])
    expect(paths('CREDIT')).toEqual(['/aprovacoes', '/clientes', '/contas', '/seguranca'])
    expect(paths('FINANCE')).toEqual(['/', '/cambio', '/seguranca'])
    expect(paths('AUDITOR')).toEqual(['/', '/auditoria', '/clientes', '/seguranca'])
    expect(canAccess(['SUPPORT'], '/cambio')).toBe(false)
    expect(canAccess(['CREDIT'], '/auditoria')).toBe(false)
    expect(canAccess(['AUDITOR'], '/usuarios')).toBe(false)
  })

  it('só o Risco bloqueia e só o Crédito mexe em limite', () => {
    expect(can(['RISK'], 'MANAGE_ACCOUNTS')).toBe(true)
    expect(can(['RISK'], 'MANAGE_LIMITS')).toBe(false)
    expect(can(['CREDIT'], 'MANAGE_LIMITS')).toBe(true)
    expect(can(['CREDIT'], 'MANAGE_ACCOUNTS')).toBe(false)
    expect(can(['AUDITOR'], 'VIEW_AUDIT')).toBe(true)
    expect(can(['AUDITOR'], 'MANAGE_ACCOUNTS')).toBe(false)
    expect(can(['CUSTOMER'], 'VIEW_CUSTOMER')).toBe(false)
  })

  it('cliente não vê nada e não é equipe', () => {
    expect(sectionsFor(['CUSTOMER'])).toEqual([])
    expect(isStaff(['CUSTOMER'])).toBe(false)
    expect(isStaff([])).toBe(false)
    expect(isStaff(['SUPPORT'])).toBe(true)
    expect(isStaff(['CREDIT'])).toBe(true)
  })

  it('toda seção tem caminho único na raiz do app', () => {
    const all = staffSections.map((s) => s.to)
    expect(new Set(all).size).toBe(all.length)
    expect(all.every((p) => p.startsWith('/'))).toBe(true)
  })
})

describe('departamentos e posições', () => {
  it('cada departamento aponta para um papel diferente', () => {
    expect(new Set(departments.map((d) => d.role)).size).toBe(departments.length)
    expect(departments.find((d) => d.code === 'DIRETORIA')?.role).toBe('ADMIN')
  })

  it('escreve o cargo como a pessoa o conhece', () => {
    expect(positionLabel('CREDITO', 'MANAGER')).toBe('Gerente · Crédito')
    expect(positionLabel('RISCO', 'ANALYST')).toBe('Analista · Risco e fraude')
    expect(positionLabel('DIRETORIA', 'MANAGER')).toBe('Diretoria')
    expect(positionLabel(undefined, undefined)).toBeNull()
    expect(departmentLabel('FINANCEIRO')).toBe('Financeiro')
  })

  it('traduz os pedidos de aprovação', () => {
    expect(approvalKindLabel('CREDIT_LIMIT')).toBe('Limite de cartão')
    expect(approvalKindLabel('ACCOUNT_UNBLOCK')).toBe('Desbloqueio de conta')
    expect(approvalStatusLabel('PENDING')).toBe('Aguardando')
    expect(approvalStatusLabel('REJECTED')).toBe('Recusado')
    expect(auditEventLabel('APPROVAL_APPROVED')).toBe('Pedido aprovado')
    expect(auditEventLabel('USER_ASSIGNED')).toBe('Posição na equipe alterada')
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
    expect(roleLabel('SUPPORT')).toBe('Atendimento')
    expect(roleLabel('RISK')).toBe('Risco e fraude')
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

describe('busca de clientes', () => {
  it('exige 3 caracteres, sem contar espaços nas pontas', () => {
    expect(searchError('')).not.toBeNull()
    expect(searchError('  ab ')).not.toBeNull()
    expect(searchError('ana')).toBeNull()
    expect(searchError(' ana ')).toBeNull()
    expect(searchError('123.456.789-09')).toBeNull()
  })

  it('traduz o estado da conta', () => {
    expect(accountStatusLabel('ACTIVE')).toBe('Ativa')
    expect(accountStatusLabel('BLOCKED')).toBe('Bloqueada')
    expect(accountStatusLabel('CLOSED')).toBe('Encerrada')
    expect(accountStatusLabel('X')).toBe('X')
  })

  it('a busca de cliente tem rótulo na auditoria', () => {
    expect(auditEventLabel('CUSTOMER_SEARCHED')).toBe('Busca de cliente')
  })
})

describe('limite de crédito pela equipe', () => {
  it('confere múltiplo, faixa e dívida', () => {
    expect(creditLimitIssue('50.000,00', '1200.00')).toBeNull()
    expect(creditLimitIssue('100.000,00', '0')).toBeNull()
    expect(creditLimitIssue('100.100,00', '0')).toMatch(/máximo/)
    expect(creditLimitIssue('1.250,00', '0')).toMatch(/múltiplo/)
    expect(creditLimitIssue('1.000,00', '1200.00')).toMatch(/já usa/)
    expect(creditLimitIssue('', '0')).not.toBeNull()
  })
})

describe('motivo do bloqueio', () => {
  it('pede de 3 a 140 caracteres em uma linha', () => {
    expect(reasonIssue('Suspeita de fraude')).toBeNull()
    expect(reasonIssue('  ab ')).not.toBeNull()
    expect(reasonIssue('x'.repeat(141))).not.toBeNull()
    expect(reasonIssue('uma\noutra')).not.toBeNull()
  })
})
