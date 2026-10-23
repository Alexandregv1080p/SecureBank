export interface Share {
  category: string
  label: string
  color: string
  value: number
  percent: number
}

/** "TEste maria" → "Teste Maria": o cadastro guarda o nome como foi digitado. */
export function titleCase(name: string): string {
  return name
    .toLowerCase()
    .split(/\s+/)
    .filter(Boolean)
    .map((w) => (['de', 'da', 'do', 'das', 'dos', 'e'].includes(w) ? w : w[0].toUpperCase() + w.slice(1)))
    .join(' ')
}

export const initials = (name: string) =>
  titleCase(name)
    .split(' ')
    .filter((w) => w.length > 2 || w === titleCase(name))
    .slice(0, 2)
    .map((w) => w[0])
    .join('')
    .toUpperCase() || '?'

/** Valor curto para o eixo do gráfico: 1,2 mil / 3,4 mi. */
export function compactBRL(value: number): string {
  const abs = Math.abs(value)
  if (abs >= 1_000_000) return `${(value / 1_000_000).toFixed(1).replace('.', ',').replace(',0', '')} mi`
  if (abs >= 1_000) return `${(value / 1_000).toFixed(1).replace('.', ',').replace(',0', '')} mil`
  return String(Math.round(value))
}

/** Teto "redondo" do eixo Y (1, 2, 2,5, 5 ou 10 × potência de 10) para o gráfico não terminar em número estranho. */
export function niceMax(value: number): number {
  if (!(value > 0)) return 1
  const pow = Math.pow(10, Math.floor(Math.log10(value)))
  const n = value / pow
  const step = n <= 1 ? 1 : n <= 2 ? 2 : n <= 2.5 ? 2.5 : n <= 5 ? 5 : 10
  return step * pow
}
