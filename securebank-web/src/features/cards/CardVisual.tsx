import { WifiHigh } from '@phosphor-icons/react'
import type { Card, CardDetails } from '../../services/types'
import { formatCardNumber, formatExpiry, statusLabel } from '../../lib/card'

/**
 * O cartão desenhado. Sempre claro sobre fundo colorido, nos dois temas, como o plástico. Sem os dados completos mostra só
 * os 4 últimos dígitos; bloqueado ou cancelado fica apagado com a situação escrita (cor sozinha não conta).
 */
export function CardVisual({ card, details }: { card: Card; details?: CardDetails | null }) {
  const off = card.status !== 'ACTIVE'
  const number = details ? formatCardNumber(details.number) : `•••• •••• •••• ${card.last4}`
  return (
    <div
      role="img"
      aria-label={`Cartão de débito final ${card.last4}, ${statusLabel[card.status].toLowerCase()}`}
      className={`relative aspect-[1.586/1] w-full max-w-sm overflow-hidden rounded-2xl p-5 text-white shadow-lg transition ${off ? 'opacity-60 grayscale' : ''}`}
      style={{ background: 'linear-gradient(135deg, #5b45e0 0%, #7a5cf0 45%, #2b2170 100%)' }}
    >
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
          {details && (
            <div className="text-right">
              <p className="text-[10px] uppercase tracking-wider text-white/70">CVV</p>
              <p className="num font-medium">{details.cvv}</p>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
