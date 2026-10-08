import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router'
import { ArrowDownLeft, ArrowUpRight, CurrencyCircleDollar } from '@phosphor-icons/react'
import { fxApi } from '../../services/fx'
import { formatDateTime } from '../../lib/format'
import { messageFor } from '../../lib/errors'
import { formatBRL } from '../../lib/money'
import { currencyName, formatForeign, rateLabel, sideLabel } from '../../lib/fx'
import { Button, ErrorState, PageHeader, Skeleton } from '../../components/ui'

/** Câmbio simulado: uma carteira por moeda, a cotação de compra e a de venda, e as últimas operações. */
export function FxPage() {
  const rates = useQuery({ queryKey: ['fx', 'rates'], queryFn: fxApi.rates, staleTime: 10_000 })
  const wallets = useQuery({ queryKey: ['fx-wallets'], queryFn: fxApi.wallets })
  const operations = useQuery({ queryKey: ['fx', 'operations'], queryFn: () => fxApi.operations(0, 10) })

  return (
    <>
      <PageHeader title="Câmbio" description="Compre e venda dólar e euro (simulado). Você paga a cotação de compra e recebe a de venda; a diferença é o spread." />

      {rates.isPending && <Skeleton className="h-48" />}
      {rates.isError && <ErrorState message={messageFor(rates.error)} onRetry={() => rates.refetch()} />}
      <ul className="grid gap-4 md:grid-cols-2">
        {rates.data?.map((r, i) => {
          const balance = wallets.data?.find((w) => w.currency === r.currency)?.balance.amount ?? '0.00'
          return (
            <li key={r.currency} className="rise" style={{ '--i': i } as React.CSSProperties}>
              <div className="card flex h-full flex-col p-6">
                <div className="flex items-center gap-3">
                  <span className="grid size-10 place-items-center rounded-xl bg-accent-soft text-accent"><CurrencyCircleDollar size={20} /></span>
                  <div>
                    <p className="font-medium">{currencyName(r.currency)}</p>
                    <p className="text-xs text-muted">Comercial R$ {rateLabel(r.mid)} · spread de {r.spreadPercent.replace('.', ',')}%</p>
                  </div>
                </div>
                <p className="mt-5 text-xs text-muted">Na carteira</p>
                <p className="num text-3xl font-semibold tracking-tight">{formatForeign(r.currency, balance)}</p>
                <dl className="mt-4 grid grid-cols-2 gap-3 text-sm">
                  <div className="rounded-xl bg-surface-2 p-3"><dt className="text-xs text-muted">Compra</dt><dd className="num font-medium">R$ {rateLabel(r.buyRate)}</dd></div>
                  <div className="rounded-xl bg-surface-2 p-3"><dt className="text-xs text-muted">Venda</dt><dd className="num font-medium">R$ {rateLabel(r.sellRate)}</dd></div>
                </dl>
                <div className="mt-5 flex gap-3">
                  <Link to={`/cambio/comprar/${r.currency}`} className="flex-1"><Button className="w-full">Comprar</Button></Link>
                  {Number(balance) > 0 ? (
                    <Link to={`/cambio/vender/${r.currency}`} className="flex-1"><Button variant="secondary" className="w-full">Vender</Button></Link>
                  ) : (
                    <Button variant="secondary" className="flex-1" disabled>Vender</Button>
                  )}
                </div>
              </div>
            </li>
          )
        })}
      </ul>

      <h2 className="mb-3 mt-10 text-sm font-medium">Últimas operações</h2>
      {operations.isPending && <Skeleton className="h-32" />}
      {operations.isError && <ErrorState message={messageFor(operations.error)} onRetry={() => operations.refetch()} />}
      {operations.data?.items.length === 0 && <p className="text-sm text-muted">Você ainda não comprou nem vendeu moeda.</p>}
      {!!operations.data?.items.length && (
        <ul className="card divide-y divide-line overflow-hidden">
          {operations.data.items.map((o) => {
            const bought = o.side === 'BUY'
            return (
              <li key={o.id} className="flex items-center gap-3 px-5 py-4">
                <span className="grid size-10 shrink-0 place-items-center rounded-xl bg-surface-2" style={{ color: bought ? 'var(--chart-1)' : 'var(--chart-2)' }}>
                  {bought ? <ArrowUpRight size={18} weight="bold" /> : <ArrowDownLeft size={18} weight="bold" />}
                </span>
                <span className="min-w-0 flex-1">
                  <span className="block text-sm font-medium">{sideLabel(o.side)} de {formatForeign(o.foreignAmount.currency, o.foreignAmount.amount)}</span>
                  <span className="block text-xs text-muted">{formatDateTime(o.createdAt)} · cotação R$ {rateLabel(o.rate)}</span>
                </span>
                <span className={`num text-sm font-medium ${bought ? '' : 'text-ok'}`}>{bought ? '−' : '+'} {formatBRL(o.brlAmount.amount)}</span>
              </li>
            )
          })}
        </ul>
      )}
    </>
  )
}
