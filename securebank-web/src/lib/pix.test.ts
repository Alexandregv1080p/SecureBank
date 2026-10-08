import { describe, expect, it } from 'vitest'
import { chargeStatusLabel, displayDay, isoDay, keyInputError, keyTypeLabel, refundAmountError, scheduleBounds, scheduleDateError, scheduleFailure, scheduleStatusLabel } from './pix'

const today = new Date(2026, 9, 6) // 6 de outubro de 2026

describe('agendamento', () => {
  it('aceita de amanhã até 365 dias à frente', () => {
    expect(scheduleBounds(today)).toEqual({ min: '2026-10-07', max: '2027-10-06' })
    expect(scheduleDateError('2026-10-07', today)).toBeNull()
    expect(scheduleDateError('2027-10-06', today)).toBeNull()
  })

  it('recusa hoje, passado, além de 365 dias e vazio', () => {
    expect(scheduleDateError('2026-10-06', today)).toMatch(/a partir de amanhã/)
    expect(scheduleDateError('2026-01-01', today)).toMatch(/a partir de amanhã/)
    expect(scheduleDateError('2027-10-07', today)).toMatch(/365/)
    expect(scheduleDateError('', today)).toBe('Escolha a data')
    expect(scheduleDateError('20/10/2026', today)).toBe('Escolha a data')
  })

  it('atravessa fim de mês e de ano', () => {
    expect(scheduleBounds(new Date(2026, 11, 31)).min).toBe('2027-01-01')
    expect(scheduleBounds(new Date(2026, 1, 28)).min).toBe('2026-03-01')
    expect(isoDay(new Date(2026, 0, 5))).toBe('2026-01-05')
  })

  it('mostra a data em dd/mm/aaaa e deixa passar o que não é data', () => {
    expect(displayDay('2026-10-20')).toBe('20/10/2026')
    expect(displayDay('lixo')).toBe('lixo')
  })
})

describe('textos', () => {
  it('traduz estados e motivos', () => {
    expect(scheduleStatusLabel('FAILED')).toBe('Não realizado')
    expect(scheduleStatusLabel('EXECUTED')).toBe('Realizado')
    expect(chargeStatusLabel('ACTIVE')).toBe('Aguardando pagamento')
    expect(chargeStatusLabel('EXPIRED')).toBe('Expirada')
    expect(scheduleFailure('INSUFFICIENT_FUNDS')).toBe('saldo insuficiente')
    expect(scheduleFailure('NOT_FOUND')).toBe('a chave não existe mais')
    expect(scheduleFailure(null)).toBe('motivo desconhecido')
    expect(keyTypeLabel('RANDOM')).toBe('Chave aleatória')
    expect(keyTypeLabel('X')).toBe('X')
  })
})

describe('validações', () => {
  it('a devolução não passa do que resta nem é zero', () => {
    expect(refundAmountError('60,00', '60.00')).toBeNull()
    expect(refundAmountError('0,01', '60.00')).toBeNull()
    expect(refundAmountError('60,01', '60.00')).toMatch(/passa/)
    expect(refundAmountError('0', '60.00')).toMatch(/maior que zero/)
    expect(refundAmountError('abc', '60.00')).toMatch(/maior que zero/)
  })

  it('a chave digitada precisa ter conteúdo', () => {
    expect(keyInputError('')).not.toBeNull()
    expect(keyInputError('  a ')).not.toBeNull()
    expect(keyInputError('ana@example.com')).toBeNull()
  })
})
