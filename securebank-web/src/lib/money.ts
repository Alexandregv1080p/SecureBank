const brl = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })

/** Só para exibição: o valor trafega e é calculado como string pela API. */
export function formatBRL(amount: string | number): string {
  return brl.format(Number(amount))
}

// 1.234 | 1.234,5 | 1.234.567,89: ponto separa milhar (grupos de 3, sem zero à esquerda) e vírgula, os centavos
const THOUSANDS = /^[1-9]\d{0,2}(\.\d{3})+(,\d{1,2})?$/
// 1234 | 1234,5
const PLAIN = /^\d+(,\d{1,2})?$/
// 1234.56 e 1.5: ponto com 1 ou 2 casas é decimal (é como a API devolve os valores)
const DOT_DECIMAL = /^\d+\.\d{1,2}$/

/**
 * Converte o que o usuário digitou ("1.234,56", "1.500", "1234.56", "10") em "1234.56". No padrão brasileiro "1.500" é
 * mil e quinhentos, não 1,50. Devolve null se não for um valor positivo com no máximo 2 casas e 13 dígitos inteiros.
 */
export function parseAmount(input: string): string | null {
  const text = input.replace(/\s|R\$/g, '')
  if (!text) return null
  let normalized: string
  if (THOUSANDS.test(text) || PLAIN.test(text)) normalized = text.replace(/\./g, '').replace(',', '.')
  else if (DOT_DECIMAL.test(text)) normalized = text
  else return null
  if (!/^\d{1,13}(\.\d{1,2})?$/.test(normalized)) return null
  return Number(normalized) > 0 ? normalized : null
}

/** a - b em centavos inteiros (nunca float), devolvido como "1234.56". */
export function subtractAmounts(a: string, b: string): string {
  const cents = Math.round(Number(a) * 100) - Math.round(Number(b) * 100)
  return (cents / 100).toFixed(2)
}

/** a + b em centavos inteiros (nunca float), devolvido como "1234.56". */
export function addAmounts(a: string, b: string): string {
  const cents = Math.round(Number(a) * 100) + Math.round(Number(b) * 100)
  return (cents / 100).toFixed(2)
}
