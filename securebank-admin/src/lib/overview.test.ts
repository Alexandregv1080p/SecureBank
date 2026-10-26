import { describe, expect, it } from 'vitest'
import { attentionItems, dayLabel, percentOf, typeLabel, typeShares } from './overview'

describe('visão gerencial', () => {
  it('agrupa o que passa de 7 tipos em "Outros" e as fatias fecham em 100%', () => {
    const byType = Array.from({ length: 9 }, (_, i) => ({ type: `T${i}`, volume: String(100 - i) }))
    const shares = typeShares(byType)
    expect(shares).toHaveLength(8)
    expect(shares[7].label).toBe('Outros')
    expect(shares.reduce((s, x) => s + x.percent, 0)).toBeCloseTo(100)
  })

  it('sem volume não há fatias', () => {
    expect(typeShares([])).toEqual([])
    expect(typeShares([{ type: 'PIX_OUT', volume: '0.00' }])).toEqual([])
  })

  it('nomeia os tipos conhecidos e mantém os desconhecidos', () => {
    expect(typeLabel('PIX_OUT')).toBe('Pix enviado')
    expect(typeLabel('NOVO')).toBe('NOVO')
  })

  it('percentual limitado a 0–100 e sem dividir por zero', () => {
    expect(percentOf('250.00', '1000.00')).toBe(25)
    expect(percentOf('2000', '1000')).toBe(100)
    expect(percentOf('10', '0')).toBe(0)
  })

  it('escreve o dia como dd/mm', () => {
    expect(dayLabel('2026-10-23')).toBe('23/10')
  })
})

describe('atenção', () => {
  const base = {
    accounts: { active: 3, blocked: 0, closed: 0, totalBalance: '0' },
    credit: { cards: 1, totalLimit: '1000', used: '0', overdueInvoices: 0, overdueAmount: '0', lateCharges: '0' },
    security: { failedLogins24h: 0, accessDenied24h: 0, outboxPending: 0 },
  }

  it('tudo em ordem não lista nada', () => {
    expect(attentionItems(base)).toEqual([])
  })

  it('lista o que está errado, do mais grave ao menos grave', () => {
    const items = attentionItems({
      accounts: { ...base.accounts, blocked: 2 },
      credit: { ...base.credit, overdueInvoices: 1, overdueAmount: '801.33' },
      security: { failedLogins24h: 30, accessDenied24h: 1, outboxPending: 500 },
    })
    expect(items.map((i) => i.key)).toEqual(['overdue', 'denied', 'logins', 'outbox', 'blocked'])
    expect(items[0].text).toContain('1 fatura vencida com saldo')
    expect(items[4].tone).toBe('warn')
  })

  it('poucos logins recusados e pouca fila não é alerta', () => {
    expect(attentionItems({ ...base, security: { failedLogins24h: 20, accessDenied24h: 0, outboxPending: 100 } })).toEqual([])
  })
})
