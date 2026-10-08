import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router'
import { investmentApi } from '../../services/investments'
import { formatDateTime } from '../../lib/format'
import { messageFor } from '../../lib/errors'
import { formatBRL } from '../../lib/money'
import { rateLabel, termLabel } from '../../lib/investment'
import { sumAmounts } from '../accounts/hooks'
import { Badge, Button, EmptyState, ErrorState, PageHeader, Skeleton } from '../../components/ui'

/** Investimentos: total aplicado (líquido hoje), suas aplicações e os produtos para aplicar. */
export function InvestmentsPage() {
  const investments = useQuery({ queryKey: ['investments'], queryFn: investmentApi.list })
  const products = useQuery({ queryKey: ['investments', 'products'], queryFn: investmentApi.products, staleTime: 5 * 60_000 })
  const active = (investments.data ?? []).filter((i) => i.status === 'ACTIVE')
  const total = sumAmounts(active.map((i) => i.net.amount))
  const yieldTotal = sumAmounts(active.map((i) => i.yield.amount))

  return (
    <>
      <PageHeader title="Investimentos" description="Renda fixa simulada. Você aplica a partir do saldo e resgata com o rendimento, já descontado o IR." />

      {investments.isPending && <Skeleton className="h-28" />}
      {investments.isError && <ErrorState message={messageFor(investments.error)} onRetry={() => investments.refetch()} />}
      {investments.data && (
        <section className="card-hero rise mb-8 p-6">
          <p className="text-sm text-muted">Total aplicado (líquido hoje)</p>
          <p className="num mt-1 text-4xl font-semibold tracking-tight">{formatBRL(total)}</p>
          <p className="mt-2 text-sm text-muted">
            {active.length === 0 ? 'Nenhuma aplicação ativa.' : `Rendimento bruto acumulado: ${formatBRL(yieldTotal)} em ${active.length} ${active.length === 1 ? 'aplicação' : 'aplicações'}.`}
          </p>
        </section>
      )}

      <h2 className="mb-3 text-sm font-medium">Suas aplicações</h2>
      {investments.data?.length === 0 && (
        <EmptyState title="Nenhuma aplicação ainda">Escolha um produto abaixo para começar.</EmptyState>
      )}
      {!!investments.data?.length && (
        <ul className="card mb-10 divide-y divide-line overflow-hidden">
          {investments.data.map((i) => (
            <li key={i.id}>
              <Link to={`/investimentos/${i.id}`} className="flex flex-wrap items-center justify-between gap-3 px-5 py-4 transition hover:bg-surface-2">
                <span className="min-w-0">
                  <span className="flex flex-wrap items-center gap-2 text-sm font-medium">
                    {i.productName} {i.status === 'REDEEMED' && <Badge>Resgatado</Badge>}
                    {i.status === 'ACTIVE' && i.canRedeem && i.termDays !== null && <Badge tone="ok">Disponível para resgate</Badge>}
                  </span>
                  <span className="block text-xs text-muted">
                    Aplicado {formatBRL(i.principal.amount)} em {formatDateTime(i.appliedAt)}
                    {i.status === 'ACTIVE' && ` · ${i.daysHeld} dias`}
                  </span>
                </span>
                <span className="text-right">
                  <span className="num block text-sm font-medium">{formatBRL(i.net.amount)}</span>
                  {i.status === 'ACTIVE' && <span className="num block text-xs text-ok">+ {formatBRL(i.yield.amount)} bruto</span>}
                </span>
              </Link>
            </li>
          ))}
        </ul>
      )}

      <h2 className="mb-3 text-sm font-medium">Produtos</h2>
      {products.isPending && <Skeleton className="h-32" />}
      {products.isError && <ErrorState message={messageFor(products.error)} onRetry={() => products.refetch()} />}
      <ul className="grid gap-4 md:grid-cols-3">
        {products.data?.map((p, i) => (
          <li key={p.code} className="rise" style={{ '--i': i } as React.CSSProperties}>
            <div className="card flex h-full flex-col p-5">
              <p className="font-medium">{p.name}</p>
              <p className="num mt-2 text-2xl font-semibold tracking-tight text-accent">{rateLabel(p.annualRatePercent)}</p>
              <p className="mt-1 text-sm text-muted">{termLabel(p.termDays)}</p>
              <p className="mt-1 text-xs text-muted">A partir de {formatBRL(p.minAmount)}</p>
              <Link to={`/investimentos/aplicar/${p.code}`} className="mt-5 block">
                <Button variant="secondary" className="w-full">Aplicar</Button>
              </Link>
            </div>
          </li>
        ))}
      </ul>
    </>
  )
}
