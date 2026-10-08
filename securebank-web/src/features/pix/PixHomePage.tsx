import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router'
import { ArrowDownLeft, ArrowUpRight, CalendarCheck, Key, PaperPlaneTilt, QrCode, Receipt } from '@phosphor-icons/react'
import { bankingApi } from '../../services/banking'
import { formatDateTime } from '../../lib/format'
import { formatBRL } from '../../lib/money'
import { Skeleton } from '../../components/ui'
import { useAccounts } from '../accounts/hooks'
import { usePixHistory } from './hooks'

const shortcuts = [
  { to: '/pix/enviar', label: 'Enviar', hint: 'Por chave ou Copia e Cola', icon: PaperPlaneTilt },
  { to: '/pix/receber', label: 'Receber', hint: 'Mostre seu QR code', icon: QrCode },
  { to: '/pix/cobrancas', label: 'Cobrar', hint: 'QR de valor fixo', icon: Receipt },
  { to: '/pix/agendados', label: 'Agendados', hint: 'Pix para uma data futura', icon: CalendarCheck },
  { to: '/pix/chaves', label: 'Minhas chaves', hint: 'CPF, e-mail, celular ou aleatória', icon: Key },
]

/** Visão geral do Pix: atalhos, o limite do dia e os últimos movimentos. */
export function PixHomePage() {
  const accounts = useAccounts()
  const first = accounts.data?.[0]
  const limits = useQuery({ queryKey: ['limits', first?.id], queryFn: () => bankingApi.limits(first!.id), enabled: !!first })
  const history = usePixHistory(0, 5)
  const pix = limits.data?.find((l) => l.type === 'PIX')
  const usedPercent = pix ? Math.min(100, (Number(pix.usedToday.amount) / Number(pix.daily.amount)) * 100) : 0

  return (
    <>
      <header className="mb-8">
        <h1 className="text-3xl font-semibold tracking-tight">Pix</h1>
        <p className="mt-1 text-sm text-muted">Envie e receba dinheiro na hora, a qualquer momento.</p>
      </header>

      <ul className="grid gap-4 sm:grid-cols-2 xl:grid-cols-5">
        {shortcuts.map(({ to, label, hint, icon: Icon }, i) => (
          <li key={to} className="rise" style={{ '--i': i } as React.CSSProperties}>
            <Link to={to} className="card block h-full p-5 transition hover:border-accent/50">
              <span className="grid size-10 place-items-center rounded-xl bg-accent-soft text-accent"><Icon size={20} /></span>
              <p className="mt-4 font-medium">{label}</p>
              <p className="mt-0.5 text-xs text-muted">{hint}</p>
            </Link>
          </li>
        ))}
      </ul>

      <div className="mt-6 grid gap-5 lg:grid-cols-[1fr_20rem]">
        <section className="card p-6">
          <div className="mb-4 flex items-center justify-between">
            <h2 className="text-sm font-medium">Últimos Pix</h2>
            <Link to="/pix/historico" className="text-sm font-medium text-accent hover:underline">Ver histórico</Link>
          </div>
          {history.isPending && <Skeleton className="h-40" />}
          {history.data?.items.length === 0 && <p className="py-6 text-center text-sm text-muted">Você ainda não fez nem recebeu um Pix.</p>}
          <ul className="divide-y divide-line">
            {history.data?.items.map((e) => {
              const received = e.direction === 'RECEIVED'
              return (
                <li key={e.id} className="flex items-center gap-3 py-3 first:pt-0 last:pb-0">
                  <span className="grid size-10 shrink-0 place-items-center rounded-xl bg-surface-2" style={{ color: received ? 'var(--chart-2)' : 'var(--chart-1)' }}>
                    {received ? <ArrowDownLeft size={18} weight="bold" /> : <ArrowUpRight size={18} weight="bold" />}
                  </span>
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-sm font-medium">{received ? 'Recebido de' : 'Enviado para'} {e.counterpartName}</span>
                    <span className="block text-xs text-muted">{formatDateTime(e.createdAt)}</span>
                  </span>
                  <span className={`num text-sm font-medium ${received ? 'text-ok' : ''}`}>{received ? '+' : '−'} {formatBRL(e.amount.amount)}</span>
                </li>
              )
            })}
          </ul>
        </section>

        <section className="card p-6">
          <h2 className="text-sm font-medium">Limite do Pix hoje</h2>
          {(accounts.isPending || limits.isPending) && <Skeleton className="mt-4 h-20" />}
          {pix && (
            <>
              <p className="num mt-3 text-2xl font-semibold">{formatBRL(pix.remainingToday.amount)}</p>
              <p className="text-xs text-muted">ainda disponível de {formatBRL(pix.daily.amount)} por dia</p>
              <div className="mt-4 h-2 overflow-hidden rounded-full bg-surface-2" role="progressbar" aria-valuenow={Math.round(usedPercent)} aria-valuemin={0} aria-valuemax={100} aria-label="Limite diário do Pix usado">
                <div className="h-full rounded-full bg-accent transition-all" style={{ width: `${usedPercent}%` }} />
              </div>
              <p className="mt-3 text-xs text-muted">Até {formatBRL(pix.perOperation.amount)} por operação.</p>
            </>
          )}
        </section>
      </div>
    </>
  )
}
