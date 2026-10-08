import { describe, expect, it } from 'vitest'
import { goalError, moveError, nameError, progress, remainingToGoal } from './piggy'

describe('nome e meta', () => {
  it('o nome é obrigatório e vai até 40 caracteres', () => {
    expect(nameError('')).not.toBeNull()
    expect(nameError('   ')).not.toBeNull()
    expect(nameError('Viagem')).toBeNull()
    expect(nameError('  Viagem  ')).toBeNull()
    expect(nameError('x'.repeat(40))).toBeNull()
    expect(nameError('x'.repeat(41))).not.toBeNull()
  })

  it('a meta é opcional, mas se vier precisa ser um valor válido', () => {
    expect(goalError('')).toBeNull()
    expect(goalError('  ')).toBeNull()
    expect(goalError('5.000,00')).toBeNull()
    expect(goalError('1000')).toBeNull()
    expect(goalError('0')).not.toBeNull()
    expect(goalError('-5')).not.toBeNull()
    expect(goalError('abc')).not.toBeNull()
    expect(goalError('10,123')).not.toBeNull()
  })
})

describe('guardar e resgatar', () => {
  it('o valor é positivo e não passa do saldo disponível', () => {
    expect(moveError('50,00')).toBeNull()
    expect(moveError('50,00', '50.00')).toBeNull()
    expect(moveError('50,01', '50.00')).toMatch(/passa/)
    expect(moveError('0', '50.00')).toMatch(/maior que zero/)
    expect(moveError('abc')).toMatch(/maior que zero/)
    expect(moveError('0,01', '0.00')).toMatch(/passa/)
  })
})

describe('progresso', () => {
  it('arredonda para baixo: 100 só quando a meta foi atingida', () => {
    expect(progress('0.00', '1000.00')).toBe(0)
    expect(progress('999.99', '1000.00')).toBe(99)
    expect(progress('1000.00', '1000.00')).toBe(100)
    expect(progress('1500.00', '1000.00')).toBe(100)
    expect(progress('250.00', '1000.00')).toBe(25)
  })

  it('sem meta não há progresso', () => {
    expect(progress('100.00', null)).toBeNull()
    expect(progress('100.00', '0')).toBeNull()
  })

  it('mostra quanto falta, sem número negativo e sem erro de ponto flutuante', () => {
    expect(remainingToGoal('250.10', '1000.00')).toBe('749.90')
    expect(remainingToGoal('0.10', '0.30')).toBe('0.20')
    expect(remainingToGoal('1000.00', '1000.00')).toBe('0.00')
    expect(remainingToGoal('1200.00', '1000.00')).toBe('0.00')
    expect(remainingToGoal('10.00', null)).toBeNull()
  })
})
