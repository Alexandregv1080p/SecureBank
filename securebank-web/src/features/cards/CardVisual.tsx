import { WifiHigh } from '@phosphor-icons/react'
import type { Card, CardDetails } from '../../services/types'
import { formatCardNumber, formatExpiry, statusLabel } from '../../lib/card'

const GRADIENT = 'linear-gradient(135deg, #5b45e0 0%, #7a5cf0 45%, #2b2170 100%)'

/**
 * O cartão desenhado, com frente e verso. Ao passar o mouse ele vira (veja `.flip-card` em index.css); em telas de toque
 * e pelo teclado quem decide é `flipped`. Sempre claro sobre fundo colorido, nos dois temas, como o plástico. Sem os dados
 * completos mostra só os 4 últimos dígitos e o CVV fica escondido no verso até o cliente pedir para mostrar. Bloqueado ou
 * cancelado fica apagado com a situação escrita (cor sozinha não conta).
 */
export function CardVisual({ card, details, flipped = false }: { card: Card; details?: CardDetails | null; flipped?: boolean }) {
  const off = card.status !== 'ACTIVE'
  const number = details ? formatCardNumber(details.number) : `•••• •••• •••• ${card.last4}`
  return (
    <div
      role="img"
      aria-label={`Cartão de débito final ${card.last4}, ${statusLabel[card.status].toLowerCase()}. O verso tem o CVV${details ? `: ${details.cvv}` : ', que aparece depois de mostrar os dados'}.`}
      data-flipped={flipped}
      className={`flip-card relative aspect-[1.586/1] w-full max-w-sm ${off ? 'opacity-60 grayscale' : ''}`}
    >
      <div className="flip-inner absolute inset-0">
        {/* frente */}
        <div className="flip-face absolute inset-0 overflow-hidden rounded-2xl p-5 text-white shadow-lg" style={{ background: GRADIENT }}>
          <div className="pointer-events-none absolute -right-10 -top-10 size-44 rounded-full bg-white/10" aria-hidden />
          <div className="pointer-events-none absolute -bottom-16 -left-8 size-48 rounded-full bg-white/5" aria-hidden />
          <div className="relative flex h-full flex-col justify-between">
            <div className="flex items-start justify-between">
              <span className="text-sm font-semibold tracking-wide">SecureBank</span>
              <span className="flex items-center gap-2 text-xs uppercase tracking-wider text-white/80">
                {off && <span className="rounded-full bg-black/30 px-2 py-0.5 font-medium">{statusLabel[card.status]}</span>}
                Débito
              </span>
            </div>
            <div className="flex items-center gap-3">
              <span className="h-7 w-9 rounded-md bg-gradient-to-br from-amber-200 to-amber-400" aria-hidden />
              <WifiHigh size={20} className="rotate-90 text-white/80" aria-hidden />
            </div>
            <p className="num text-lg font-medium tracking-widest sm:text-xl">{number}</p>
            <div className="flex items-end justify-between gap-3 text-xs">
              <div className="min-w-0">
                <p className="text-[10px] uppercase tracking-wider text-white/70">Titular</p>
                <p className="truncate font-medium uppercase tracking-wide">{card.holderName}</p>
              </div>
              <div className="text-right">
                <p className="text-[10px] uppercase tracking-wider text-white/70">Validade</p>
                <p className="num font-medium">{formatExpiry(card.expiryMonth, card.expiryYear)}</p>
              </div>
            </div>
          </div>
        </div>

        {/* verso */}
        <div className="flip-face flip-back absolute inset-0 overflow-hidden rounded-2xl text-white shadow-lg" style={{ background: GRADIENT }} aria-hidden>
          <div className="pointer-events-none absolute -left-12 -top-14 size-48 rounded-full bg-white/5" aria-hidden />
          <div className="mt-6 h-11 w-full bg-black/80" />
          <div className="relative flex flex-col gap-3 px-5 pt-4">
            <div className="flex items-center gap-2">
              <div className="h-9 flex-1 rounded-sm bg-white/90" style={{ backgroundImage: 'repeating-linear-gradient(45deg, rgb(0 0 0 / 0.07) 0 6px, transparent 6px 12px)' }} />
              <div className="flex h-9 w-16 flex-col items-center justify-center rounded-sm bg-white text-[#1b1550]">
                <span className="text-[8px] font-semibold uppercase leading-none tracking-wider text-[#5b45e0]">CVV</span>
                <span className="num text-sm font-semibold leading-tight">{details ? details.cvv : '•••'}</span>
              </div>
            </div>
            <p className="text-[10px] leading-snug text-white/80">
              Cartão de débito virtual do SecureBank, de uso simulado e sem valor real. A compra sai direto do saldo da conta.
            </p>
            <div className="flex items-end justify-between text-[10px] text-white/70">
              <span>Dúvidas ou perda: pelo app, em Cartões</span>
              <span className="num text-xs font-medium text-white">•••• {card.last4}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
