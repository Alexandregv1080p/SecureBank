const brl = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })

/** Só para exibição: o valor trafega e é calculado como string pela API. */
export function formatBRL(amount: string | number): string {
  return brl.format(Number(amount))
}

/**
 * Converte o que o usuário digitou ("1.234,56", "1234.56", "10") em "1234.56".
 * Devolve null se não for um valor positivo com no máximo 2 casas.
 */
export function parseAmount(input: string): string | null {
  const text = input.replace(/\s|R\$/g, '')
  if (!text) return null
  const normalized = text.includes(',') ? text.replace(/\./g, '').replace(',', '.') : text
  if (!/^\d{1,13}(\.\d{1,2})?$/.test(normalized)) return null
  return Number(normalized) > 0 ? normalized : null
}
