import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router'
import { ArrowDown, ArrowUp, CaretLeft, CaretRight } from '@phosphor-icons/react'
import { bankingApi } from '../../services/banking'
import { describeTransaction as describe } from '../../lib/transactions'
import { formatBRL } from '../../lib/money'
import { accountLabel, accountTypeLabel, formatDateTime } from '../../lib/format'
import { messageFor } from '../../lib/errors'
import { Badge, Button, EmptyState, ErrorState, Input, PageHeader, Panel, Skeleton } from '../../components/ui'
import { MoneyForm } from './MoneyForm'

const limitLabels = { WITHDRAW: 'Saque', TRANSFER: 'Transferência', PAYMENT: 'Pagamento', PIX: 'Pix', FX: 'Câmbio' } as const

export function AccountDetailPage() {
  const { id = '' } = useParams()
  const [tab, setTab] = useState<'deposit' | 'withdraw'>('deposit')
  const account = useQuery({ queryKey: ['account', id], queryFn: () => bankingApi.account(id) })
  const limits = useQuery({ queryKey: ['limits', id], queryFn: () => bankingApi.limits(id) })

  if (account.isError) {
    return <ErrorState message={messageFor(account.error)} onRetry={() => account.refetch()} />
  }
  const a = account.data

  return (
    <>
      <Link to="/contas" className="mb-4 inline-flex items-center gap-1 text-sm text-muted hover:text-ink">
        <CaretLeft size={14} /> Contas
      </Link>
      <PageHeader
        title={a ? accountTypeLabel(a.type) : 'Conta'}
        description={a ? accountLabel(a) : undefined}
        action={a && a.status !== 'ACTIVE' ? <Badge tone="warn">{a.status === 'BLOCKED' ? 'Conta bloqueada' : 'Conta encerrada'}</Badge> : undefined}
      />

      <div className="mb-10">
        <p className="text-sm text-muted">Saldo disponível</p>
        {a ? <p className="num mt-1 text-4xl font-semibold tracking-tight">{formatBRL(a.balance.amount)}</p> : <Skeleton className="mt-2 h-10 w-56" />}
      </div>

      <div className="grid gap-8 lg:grid-cols-2">
        <Panel>
          <div role="tablist" aria-label="Operação" className="mb-5 flex gap-1 rounded-ui bg-bg p-1">
            {(['deposit', 'withdraw'] as const).map((k) => (
              <button
                key={k}
                role="tab"
                aria-selected={tab === k}
                onClick={() => setTab(k)}
                className={`flex-1 rounded-ui px-3 py-2 text-sm font-medium transition ${tab === k ? 'bg-surface shadow-sm' : 'text-muted hover:text-ink'}`}
              >
                {k === 'deposit' ? 'Depositar' : 'Sacar'}
              </button>
            ))}
          </div>
          <MoneyForm key={tab} accountId={id} kind={tab} balance={a?.balance.amount} limit={limits.data?.find((l) => l.type === 'WITHDRAW')} />
        </Panel>

        <Panel title="Limites de hoje">
          {limits.isPending && <Skeleton className="h-28" />}
          {limits.isError && <p className="text-sm text-muted">Não foi possível carregar os limites.</p>}
          {limits.data && (
            <dl className="flex flex-col gap-4">
              {limits.data.map((l) => (
                <div key={l.type} className="flex items-baseline justify-between gap-4">
                  <div>
                    <dt className="text-sm font-medium">{limitLabels[l.type]}</dt>
                    <dd className="text-xs text-muted">Até {formatBRL(l.perOperation.amount)} por operação</dd>
                  </div>
                  <dd className="num text-right text-sm">
                    {formatBRL(l.remainingToday.amount)}
                    <span className="block text-xs text-muted">restante hoje</span>
                  </dd>
                </div>
              ))}
            </dl>
          )}
        </Panel>
      </div>

      <Statement accountId={id} />
    </>
  )
}

function Statement({ accountId }: { accountId: string }) {
  const [page, setPage] = useState(0)
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const statement = useQuery({
    queryKey: ['statement', accountId, page, from, to],
    queryFn: () => bankingApi.statement(accountId, page, from || undefined, to || undefined),
    placeholderData: (previous) => previous,
  })
  const totalPages = statement.data ? Math.max(1, Math.ceil(statement.data.totalElements / statement.data.size)) : 1

  return (
    <section className="mt-10">
      <div className="mb-4 flex flex-wrap items-end justify-between gap-4">
        <h2 className="text-lg font-semibold">Extrato</h2>
        <div className="flex flex-wrap items-end gap-3">
          <label className="flex flex-col gap-1 text-xs text-muted">
            De
            <Input type="date" value={from} onChange={(e) => { setFrom(e.target.value); setPage(0) }} className="w-40" />
          </label>
          <label className="flex flex-col gap-1 text-xs text-muted">
            Até
            <Input type="date" value={to} onChange={(e) => { setTo(e.target.value); setPage(0) }} className="w-40" />
          </label>
        </div>
      </div>

      {statement.isPending && <Skeleton className="h-48" />}
      {statement.isError && <ErrorState message={messageFor(statement.error)} onRetry={() => statement.refetch()} />}
      {statement.data?.items.length === 0 && <EmptyState title="Nenhuma movimentação">Não há lançamentos neste período.</EmptyState>}
      {!!statement.data?.items.length && (
        <>
          <ul className="divide-y divide-line card overflow-hidden">
            {statement.data.items.map((t) => {
              const credit = t.direction === 'CREDIT'
              return (
                <li key={t.id} className="flex items-center justify-between gap-4 px-5 py-4">
                  <div className="flex items-center gap-4">
                    <span className={`grid size-9 shrink-0 place-items-center rounded-ui ${credit ? 'bg-ok-soft text-ok' : 'bg-bg text-muted'}`}>
                      {credit ? <ArrowDown size={18} /> : <ArrowUp size={18} />}
                    </span>
                    <div>
                      <p className="text-sm font-medium">{describe(t)}</p>
                      <p className="text-xs text-muted">{formatDateTime(t.createdAt)}</p>
                    </div>
                  </div>
                  <div className="text-right">
                    <p className={`num text-sm font-medium ${credit ? 'text-ok' : ''}`}>
                      {credit ? '+' : '-'} {formatBRL(t.amount.amount)}
                    </p>
                    <p className="num text-xs text-muted">saldo {formatBRL(t.balanceAfter.amount)}</p>
                  </div>
                </li>
              )
            })}
          </ul>
          <div className="mt-4 flex items-center justify-between text-sm text-muted">
            <span>
              Página {page + 1} de {totalPages}
            </span>
            <div className="flex gap-2">
              <Button variant="secondary" disabled={page === 0} onClick={() => setPage((p) => p - 1)} aria-label="Página anterior">
                <CaretLeft size={16} />
              </Button>
              <Button variant="secondary" disabled={page + 1 >= totalPages} onClick={() => setPage((p) => p + 1)} aria-label="Próxima página">
                <CaretRight size={16} />
              </Button>
            </div>
          </div>
        </>
      )}
    </section>
  )
}
