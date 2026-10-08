import { describe, expect, it } from 'vitest'
import { smoothPath, type Point } from './chart'

describe('curva do gráfico', () => {
  it('não desenha nada sem pontos e é só um ponto com um valor', () => {
    expect(smoothPath([])).toBe('')
    expect(smoothPath([[10, 20]])).toBe('M10,20')
  })

  it('passa por todos os pontos, na ordem', () => {
    const pts: Point[] = [[0, 100], [50, 40], [100, 80], [150, 10]]
    const d = smoothPath(pts)

    expect(d.startsWith('M0,100')).toBe(true)
    expect(d.match(/ C/g)).toHaveLength(3)
    for (const [x, y] of pts.slice(1)) expect(d).toContain(` ${x.toFixed(2)},${y.toFixed(2)}`)
  })

  it('nunca estoura acima nem abaixo dos pontos vizinhos (não passa do eixo)', () => {
    // vale em baixo (y=200) seguido de pico: sem o limite, a curva afundaria abaixo da linha de base
    const pts: Point[] = [[0, 200], [40, 200], [80, 20], [120, 200], [160, 200]]
    const numbers = [...smoothPath(pts).matchAll(/C([\d.]+),([\d.]+) ([\d.]+),([\d.]+) ([\d.]+),([\d.]+)/g)]

    numbers.forEach((m, i) => {
      const lo = Math.min(pts[i][1], pts[i + 1][1])
      const hi = Math.max(pts[i][1], pts[i + 1][1])
      for (const y of [Number(m[2]), Number(m[4])]) {
        expect(y).toBeGreaterThanOrEqual(lo)
        expect(y).toBeLessThanOrEqual(hi)
      }
    })
  })
})
