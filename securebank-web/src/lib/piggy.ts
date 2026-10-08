import { parseAmount } from './money'

export const NAME_MAX = 40
export const MAX_ACTIVE = 20

/** Nome do porquinho: obrigatório, até 40 caracteres, sem contar espaços nas pontas. */
export function nameError(input: string): string | null {
  const name = input.trim()
  if (!name) return 'Dê um nome ao porquinho'
  if (name.length > NAME_MAX) return `No máximo ${NAME_MAX} caracteres`
  return null
}

/** Meta é opcional; se vier, tem que ser um valor positivo com até 2 casas. */
export function goalError(input: string): string | null {
  if (!input.trim()) return null
  return parseAmount(input) === null ? 'Informe um valor maior que zero, com até 2 casas decimais' : null
}

/** Quanto guardar/resgatar: positivo e, quando há limite (saldo da conta ou do porquinho), não maior que ele. */
export function moveError(input: string, available?: string): string | null {
  const parsed = parseAmount(input)
  if (parsed === null) return 'Informe um valor maior que zero, com até 2 casas decimais'
  if (available !== undefined && Math.round(Number(parsed) * 100) > Math.round(Number(available) * 100)) return 'O valor passa do saldo disponível'
  return null
}

/** 0 a 100 (arredondado para baixo, então 100 só quando a meta foi mesmo atingida); sem meta, nulo. */
export function progress(balance: string, goal: string | null): number | null {
  if (goal === null || !(Number(goal) > 0)) return null
  return Math.min(100, Math.floor((Number(balance) / Number(goal)) * 100))
}

/** Quanto falta para a meta (nunca negativo); sem meta, nulo. */
export function remainingToGoal(balance: string, goal: string | null): string | null {
  if (goal === null) return null
  const cents = Math.max(0, Math.round(Number(goal) * 100) - Math.round(Number(balance) * 100))
  return (cents / 100).toFixed(2)
}
