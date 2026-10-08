import { useEffect, useId, useRef, useState, type PointerEvent } from 'react'
import { compactBRL, niceMax, type Share } from '../lib/dashboard'
import { smoothPath, type Point } from '../lib/chart'
import { formatBRL } from '../lib/money'

export interface Series {
  name: string
  color: string
  values: number[]
}

const H = 250
const PAD = { l: 44, r: 12, t: 14, b: 28 }

/**
 * Gráfico de área com duas ou mais séries (ex.: entradas e saídas por mês). Passar o mouse (ou o dedo) mostra os
 * valores do mês; uma tabela escondida entrega os mesmos dados para leitor de tela.
 */
export function AreaChart({ labels, series, title }: { labels: string[]; series: Series[]; title: string }) {
  const uid = useId()
  const box = useRef<HTMLDivElement>(null)
  // Desenha na largura real do cartão: o texto dos eixos fica no tamanho certo em qualquer tela (nada de SVG encolhido).
  const [W, setW] = useState(640)
  useEffect(() => {
    const el = box.current
    if (!el) return
    const observer = new ResizeObserver(([entry]) => setW(Math.max(260, Math.round(entry.contentRect.width))))
    observer.observe(el)
    return () => observer.disconnect()
  }, [])
  const [hover, setHover] = useState<number | null>(null)
  const n = labels.length
  const innerW = W - PAD.l - PAD.r
  const innerH = H - PAD.t - PAD.b
  const max = niceMax(Math.max(0, ...series.flatMap((s) => s.values)))
  const x = (i: number) => (n === 1 ? PAD.l + innerW / 2 : PAD.l + (i * innerW) / (n - 1))
  const y = (v: number) => PAD.t + innerH * (1 - v / max)
  const ticks = [0, 1, 2, 3, 4].map((t) => (max * t) / 4)

  function move(e: PointerEvent<HTMLDivElement>) {
    const rect = e.currentTarget.getBoundingClientRect()
    const px = ((e.clientX - rect.left) / rect.width) * W
    const i = n === 1 ? 0 : Math.round(((px - PAD.l) / innerW) * (n - 1))
    setHover(Math.min(n - 1, Math.max(0, i)))
  }

  return (
    <div ref={box} className="relative" onPointerMove={move} onPointerLeave={() => setHover(null)}>
      <svg viewBox={`0 0 ${W} ${H}`} width={W} height={H} role="img" aria-label={title} className="block max-w-full">
        <defs>
          {series.map((s, i) => (
            <linearGradient key={s.name} id={`${uid}-g${i}`} x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor={s.color} stopOpacity="0.32" />
              <stop offset="100%" stopColor={s.color} stopOpacity="0" />
            </linearGradient>
          ))}
        </defs>

        {ticks.map((t) => (
          <g key={t}>
            <line x1={PAD.l} x2={W - PAD.r} y1={y(t)} y2={y(t)} stroke="var(--border)" strokeDasharray={t === 0 ? undefined : '3 5'} strokeWidth="1" />
            <text x={PAD.l - 10} y={y(t) + 4} textAnchor="end" fontSize="11" fill="var(--muted)">
              {compactBRL(t)}
            </text>
          </g>
        ))}
        {labels.map((l, i) => (
          <text key={l + i} x={x(i)} y={H - 8} textAnchor="middle" fontSize="11" fill={hover === i ? 'var(--text)' : 'var(--muted)'}>
            {l}
          </text>
        ))}

        {series.map((s, si) => {
          const pts: Point[] = s.values.map((v, i) => [x(i), y(v)])
          const line = smoothPath(pts)
          const area = `${line} L${x(n - 1)},${y(0)} L${x(0)},${y(0)} Z`
          return (
            <g key={s.name}>
              <path d={area} fill={`url(#${uid}-g${si})`} />
              <path d={line} pathLength={1} className="draw" fill="none" stroke={s.color} strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" />
            </g>
          )
        })}

        {hover !== null && (
          <g>
            <line x1={x(hover)} x2={x(hover)} y1={PAD.t} y2={y(0)} stroke="var(--muted)" strokeOpacity="0.5" strokeDasharray="3 4" />
            {series.map((s) => (
              <circle key={s.name} cx={x(hover)} cy={y(s.values[hover])} r="5" fill="var(--panel-solid)" stroke={s.color} strokeWidth="2.5" />
            ))}
          </g>
        )}
      </svg>

      {hover !== null && (
        <div
          className="pointer-events-none absolute top-2 z-10 min-w-36 rounded-xl border border-line bg-panel px-3 py-2 text-xs shadow-xl"
          style={{ left: `${(x(hover) / W) * 100}%`, transform: `translateX(${hover === 0 ? '-10%' : hover === n - 1 ? '-90%' : '-50%'})` }}
        >
          <p className="mb-1 font-medium">{labels[hover]}</p>
          {series.map((s) => (
            <p key={s.name} className="flex items-center justify-between gap-4">
              <span className="flex items-center gap-1.5 text-muted">
                <span className="size-2 rounded-full" style={{ background: s.color }} />
                {s.name}
              </span>
              <span className="num font-medium">{formatBRL(s.values[hover])}</span>
            </p>
          ))}
        </div>
      )}

      <table className="sr-only">
        <caption>{title}</caption>
        <thead>
          <tr>
            <th>Mês</th>
            {series.map((s) => (
              <th key={s.name}>{s.name}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {labels.map((l, i) => (
            <tr key={l + i}>
              <td>{l}</td>
              {series.map((s) => (
                <td key={s.name}>{formatBRL(s.values[i])}</td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

/** Rosca de participação; o miolo mostra o total. As fatias têm um respiro entre si para ficarem legíveis. */
export function Donut({ shares, centerLabel, centerValue }: { shares: Share[]; centerLabel: string; centerValue: string }) {
  const r = 68
  const c = 2 * Math.PI * r
  const gap = shares.length > 1 ? 4 : 0
  const offsets = shares.map((_, i) => shares.slice(0, i).reduce((acc, s) => acc + (s.percent / 100) * c, 0))
  return (
    <svg viewBox="0 0 180 180" role="img" aria-label={`${centerLabel}: ${centerValue}`} className="mx-auto size-44">
      <circle cx="90" cy="90" r={r} fill="none" stroke="var(--border)" strokeWidth="16" strokeOpacity="0.55" />
      <g transform="rotate(-90 90 90)">
        {shares.map((s, i) => {
          const len = Math.max(0, (s.percent / 100) * c - gap)
          return (
            <circle
              key={s.category}
              cx="90"
              cy="90"
              r={r}
              fill="none"
              stroke={s.color}
              strokeWidth="16"
              strokeLinecap="round"
              strokeDasharray={`${len} ${c - len}`}
              strokeDashoffset={-offsets[i]}
            />
          )
        })}
      </g>
      <text x="90" y="84" textAnchor="middle" fontSize="11" fill="var(--muted)">
        {centerLabel}
      </text>
      <text x="90" y="104" textAnchor="middle" fontSize="16" fontWeight="600" fill="var(--text)" className="num">
        {centerValue}
      </text>
    </svg>
  )
}

/** Mini gráfico de tendência para os cartões de métrica (sem eixos: só o formato da série). */
export function Sparkline({ values, color, className = 'h-10 w-28' }: { values: number[]; color: string; className?: string }) {
  const uid = useId()
  const w = 120
  const h = 40
  const max = Math.max(...values, 0)
  const min = Math.min(...values, 0)
  const span = max - min || 1
  const pts: Point[] = values.map((v, i) => [values.length === 1 ? w / 2 : (i * (w - 6)) / (values.length - 1) + 3, h - 4 - ((v - min) / span) * (h - 8)])
  const line = smoothPath(pts)
  const last = pts[pts.length - 1]
  return (
    <svg viewBox={`0 0 ${w} ${h}`} aria-hidden className={className}>
      <defs>
        <linearGradient id={uid} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor={color} stopOpacity="0.35" />
          <stop offset="100%" stopColor={color} stopOpacity="0" />
        </linearGradient>
      </defs>
      {last && <path d={`${line} L${last[0]},${h} L${pts[0][0]},${h} Z`} fill={`url(#${uid})`} />}
      <path d={line} pathLength={1} className="draw" fill="none" stroke={color} strokeWidth="2" strokeLinecap="round" />
      {last && <circle cx={last[0]} cy={last[1]} r="3" fill={color} />}
    </svg>
  )
}

/** Anel de progresso (0–100) com ícone ou texto no miolo: usado nas listas laterais do painel. */
export function ProgressRing({ percent, color, children, size = 52 }: { percent: number; color: string; children?: React.ReactNode; size?: number }) {
  const r = 20
  const c = 2 * Math.PI * r
  const p = Math.min(100, Math.max(0, percent))
  return (
    <span className="relative inline-grid shrink-0 place-items-center" style={{ width: size, height: size }}>
      <svg viewBox="0 0 52 52" aria-hidden className="absolute inset-0 size-full -rotate-90">
        <circle cx="26" cy="26" r={r} fill="none" stroke="var(--border)" strokeWidth="5" />
        <circle cx="26" cy="26" r={r} fill="none" stroke={color} strokeWidth="5" strokeLinecap="round" strokeDasharray={`${(p / 100) * c} ${c}`} />
      </svg>
      <span className="relative text-[10px] font-semibold">{children}</span>
    </span>
  )
}
