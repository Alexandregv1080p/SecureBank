import { useMemo, useState } from 'react'
import { useQueries, useQuery } from '@tanstack/react-query'
import { Link, Navigate } from 'react-router'
import { ArrowDownLeft, ArrowUpRight, ArrowsLeftRight, Barcode, CurrencyCircleDollar, PiggyBank, TrendUp, Wallet } from '@phosphor-icons/react'
import { bankingApi } from '../../services/banking'
import { useAuth } from '../../stores/auth'
import type { Transaction } from '../../services/types'
import { formatBRL } from '../../lib/money'
import { accountLabel, accountTypeLabel, formatDateTime } from '../../lib/format'
import { messageFor } from '../../lib/errors'
import { combineMonths, deltaPercent, expenseShares, lastMonths, monthShort, titleCase } from '../../lib/dashboard'
import { describeTransaction } from '../../lib/transactions'
import { Button, EmptyState, ErrorState, Skeleton } from '../../components/ui'
import { AreaChart, Donut, ProgressRing, Sparkline } from '../../components/charts'
import { useAccounts, sumAmounts } from '../accounts/hooks'

export function DashboardPage() {
  const isCustomer = useAuth((s) => s.claims?.roles.includes('CUSTOMER'))
  if (!isCustomer) return <Navigate to="/equipe" replace />
  return <CustomerDashboard />
}

const today = new Intl.DateTimeFormat('pt-BR', { weekday: 'long', day: 'numeric', month: 'long' })
const brl = (n: number) => formatBRL(n)

function CustomerDashboard() {
  const me = useQuery({ queryKey: ['me'], queryFn: bankingApi.me, staleTime: 5 * 60_000 })
  const accounts = useAccounts()
  const firstName = me.data ? titleCase(me.data.name).split(' ')[0] : ''
  const list = accounts.data ?? []
  const [now] = useState(() => new Date())
  const months = useMemo(() => lastMonths(6, now), [now])

  // Um resumo por conta e por mês (os últimos 6): somados no navegador para o gráfico e as métricas.
  const summaries = useQueries({
    queries: list.flatMap((a) => months.map((m) => ({ queryKey: ['summary', a.id, m], queryFn: () => bankingApi.statementSummary(a.id, m), staleTime: 60_000 }))),
  })
  const recent = useQueries({
    queries: list.map((a) => ({ queryKey: ['statement', a.id, 0, undefined, undefined], queryFn: () => bankingApi.statement(a.id, 0), staleTime: 30_000 })),
  })
  const investments = useQuery({ queryKey: ['investments'], queryFn: bankingApi.investments })
  const piggies = useQuery({ queryKey: ['piggies'], queryFn: bankingApi.piggies })
  const wallets = useQuery({ queryKey: ['fx-wallets'], queryFn: bankingApi.fxWallets })

  const summariesLoading = accounts.isPending || summaries.some((q) => q.isPending)
  const perMonth = months.map((_, mi) => list.map((_, ai) => summaries[ai * months.length + mi]?.data))
  const totals = combineMonths(months, perMonth)
  const current = totals[totals.length - 1]
  const previous = totals[totals.length - 2]
  const hasMovement = totals.some((t) => t.income > 0 || t.expenses > 0)
  const shares = expenseShares(perMonth[perMonth.length - 1])
  const expensesTotal = shares.reduce((a, s) => a + s.value, 0)

  const balance = Number(sumAmounts(list.map((a) => a.balance.amount)))
  const invested = Number(sumAmounts((investments.data ?? []).filter((i) => i.status === 'ACTIVE').map((i) => i.net.amount)))
  const saved = Number(sumAmounts((piggies.data ?? []).map((p) => p.balance.amount)))
  const patrimony = balance + invested + saved
  const pct = (v: number) => (patrimony > 0 ? (v / patrimony) * 100 : 0)

  const names = new Map(list.map((a) => [a.id, accountTypeLabel(a.type)]))
  const latest: (Transaction & { account: string })[] = recent
    .flatMap((q, i) => (q.data?.items ?? []).map((t) => ({ ...t, account: names.get(list[i]?.id) ?? '' })))
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
    .slice(0, 7)

  const net = current.income - current.expenses
  const savingRate = current.income > 0 ? Math.max(0, (net / current.income) * 100) : 0

  return (
    <>
      <header className="rise mb-8 flex flex-wrap items-end justify-between gap-4" style={{ '--i': 0 } as React.CSSProperties}>
        <div>
          <p className="text-sm text-muted first-letter:uppercase">{today.format(now)}</p>
          <h1 className="mt-1 text-3xl font-semibold tracking-tight">{firstName ? `Olá, ${firstName}` : 'Olá'}</h1>
        </div>
        <div className="flex gap-2">
          <Link to="/transferir">
            <Button>
              <ArrowsLeftRight size={18} /> Transferir
            </Button>
          </Link>
          <Link to="/pagar">
            <Button variant="secondary">
              <Barcode size={18} /> Pagar
            </Button>
          </Link>
        </div>
      </header>

      {accounts.isError && <ErrorState message={messageFor(accounts.error)} onRetry={() => accounts.refetch()} />}
      {accounts.data?.length === 0 && (
        <EmptyState
          title="Você ainda não tem uma conta"
          action={
            <Link to="/contas">
              <Button>Abrir conta</Button>
            </Link>
          }
        >
          Abra uma conta corrente ou poupança para começar a movimentar.
        </EmptyState>
      )}

      {list.length > 0 && (
        <div className="grid gap-5 md:grid-cols-2 xl:grid-cols-4 [&>*]:min-w-0">
          {/* Resumo do saldo */}
          <section className="card rise p-6 md:col-span-2" style={{ '--i': 1 } as React.CSSProperties}>
            <div className="mb-5 flex flex-wrap items-start justify-between gap-4">
              <div>
                <h2 className="text-sm font-medium text-muted">Saldo total</h2>
                <p className="num mt-1 text-4xl font-semibold tracking-tight">{brl(balance)}</p>
                <p className="mt-1 text-xs text-muted">em {list.length} {list.length === 1 ? 'conta' : 'contas'}</p>
              </div>
              <ul className="flex gap-5 text-sm" aria-label="Legenda">
                <li className="flex items-center gap-2">
                  <span className="grid size-8 place-items-center rounded-lg bg-surface-2" style={{ color: 'var(--chart-2)' }}>
                    <ArrowDownLeft size={16} weight="bold" />
                  </span>
                  <span>
                    <span className="block text-xs text-muted">Entradas ({monthShort(current.month)})</span>
                    <span className="num font-medium">{brl(current.income)}</span>
                  </span>
                </li>
                <li className="flex items-center gap-2">
                  <span className="grid size-8 place-items-center rounded-lg bg-surface-2" style={{ color: 'var(--chart-1)' }}>
                    <ArrowUpRight size={16} weight="bold" />
                  </span>
                  <span>
                    <span className="block text-xs text-muted">Saídas ({monthShort(current.month)})</span>
                    <span className="num font-medium">{brl(current.expenses)}</span>
                  </span>
                </li>
              </ul>
            </div>
            {summariesLoading && <Skeleton className="h-60" />}
            {!summariesLoading && hasMovement && (
              <AreaChart
                title="Entradas e saídas dos últimos 6 meses"
                labels={totals.map((t) => monthShort(t.month))}
                series={[
                  { name: 'Entradas', color: 'var(--chart-2)', values: totals.map((t) => t.income) },
                  { name: 'Saídas', color: 'var(--chart-1)', values: totals.map((t) => t.expenses) },
                ]}
              />
            )}
            {!summariesLoading && !hasMovement && (
              <div className="grid h-60 place-items-center rounded-2xl border border-dashed border-line text-center">
                <div>
                  <p className="font-medium">Sem movimentação nos últimos 6 meses</p>
                  <p className="mx-auto mt-1 max-w-xs text-sm text-muted">Quando você depositar, transferir ou pagar, o gráfico mostra as entradas e saídas mês a mês.</p>
                </div>
              </div>
            )}
          </section>

          {/* Métricas do mês */}
          <div className="grid gap-5 md:col-span-1">
            <Stat
              i={2}
              label="Entradas do mês"
              value={brl(current.income)}
              delta={deltaPercent(current.income, previous.income)}
              goodWhen="up"
              visual={<Sparkline values={totals.map((t) => t.income)} color="var(--chart-2)" className="h-9 w-24" />}
            />
            <Stat
              i={3}
              label="Saídas do mês"
              value={brl(current.expenses)}
              delta={deltaPercent(current.expenses, previous.expenses)}
              goodWhen="down"
              visual={<Sparkline values={totals.map((t) => t.expenses)} color="var(--chart-1)" className="h-9 w-24" />}
            />
            <Stat
              i={4}
              label="Resultado do mês"
              value={`${net < 0 ? '−' : ''}${brl(Math.abs(net))}`}
              hint={current.income > 0 ? `${savingRate.toFixed(0).replace('.', ',')}% da entrada sobrou` : 'Sem entradas neste mês'}
              visual={
                <ProgressRing percent={savingRate} color="var(--chart-6)" size={44}>
                  {Math.round(savingRate)}%
                </ProgressRing>
              }
            />
          </div>

          {/* Patrimônio */}
          <section className="card rise p-6 md:col-span-1" style={{ '--i': 5 } as React.CSSProperties}>
            <h2 className="text-sm font-medium">Onde está o seu dinheiro</h2>
            <p className="mt-0.5 text-xs text-muted">Patrimônio de {brl(patrimony)}</p>
            <ul className="mt-5 flex flex-col gap-4">
              <Holding icon={<Wallet size={16} />} label="Em conta" value={balance} percent={pct(balance)} color="var(--chart-2)" />
              <Holding icon={<TrendUp size={16} />} label="Investido" value={invested} percent={pct(invested)} color="var(--chart-1)" loading={investments.isPending} />
              <Holding icon={<PiggyBank size={16} />} label="Porquinhos" value={saved} percent={pct(saved)} color="var(--chart-3)" loading={piggies.isPending} />
            </ul>
            {(wallets.data ?? []).some((w) => Number(w.balance.amount) > 0) && (
              <div className="mt-5 border-t border-line pt-4">
                <p className="mb-2 flex items-center gap-2 text-xs font-medium text-muted">
                  <CurrencyCircleDollar size={16} /> Moeda estrangeira
                </p>
                <ul className="flex flex-col gap-1.5 text-sm">
                  {wallets.data!
                    .filter((w) => Number(w.balance.amount) > 0)
                    .map((w) => (
                      <li key={w.currency} className="flex justify-between">
                        <span className="text-muted">{w.currency}</span>
                        <span className="num font-medium">{Number(w.balance.amount).toLocaleString('pt-BR', { minimumFractionDigits: 2 })}</span>
                      </li>
                    ))}
                </ul>
              </div>
            )}
          </section>

          {/* Saídas por categoria */}
          <section className="card rise p-6" style={{ '--i': 6 } as React.CSSProperties}>
            <h2 className="text-sm font-medium">Saídas por categoria</h2>
            <p className="mt-0.5 text-xs text-muted">Este mês</p>
            {summariesLoading && <Skeleton className="mt-5 h-44" />}
            {!summariesLoading && shares.length === 0 && <p className="mt-6 text-sm text-muted">Nenhuma saída neste mês.</p>}
            {!summariesLoading && shares.length > 0 && (
              <>
                <div className="mt-4">
                  <Donut shares={shares} centerLabel="Saídas" centerValue={brl(expensesTotal)} />
                </div>
                <ul className="mt-4 flex flex-col gap-2 text-sm">
                  {shares.map((s) => (
                    <li key={s.category} className="flex items-center justify-between gap-3">
                      <span className="flex items-center gap-2">
                        <span className="size-2.5 rounded-full" style={{ background: s.color }} />
                        {s.label}
                      </span>
                      <span className="text-muted">{s.percent.toFixed(0)}%</span>
                    </li>
                  ))}
                </ul>
              </>
            )}
          </section>

          {/* Movimentações recentes */}
          <section className="card rise p-6 md:col-span-1 xl:col-span-2" style={{ '--i': 7 } as React.CSSProperties}>
            <div className="mb-4 flex items-center justify-between">
              <h2 className="text-sm font-medium">Movimentações recentes</h2>
              <Link to="/contas" className="text-sm font-medium text-accent hover:underline">
                Ver contas
              </Link>
            </div>
            {recent.some((q) => q.isPending) && <Skeleton className="h-56" />}
            {!recent.some((q) => q.isPending) && latest.length === 0 && <p className="py-8 text-center text-sm text-muted">Nenhuma movimentação ainda.</p>}
            <ul className="divide-y divide-line">
              {latest.map((t) => {
                const credit = t.direction === 'CREDIT'
                return (
                  <li key={t.id} className="flex items-center gap-3 py-3 first:pt-0 last:pb-0">
                    <span className="grid size-10 shrink-0 place-items-center rounded-xl bg-surface-2" style={{ color: credit ? 'var(--chart-2)' : 'var(--chart-1)' }}>
                      {credit ? <ArrowDownLeft size={18} weight="bold" /> : <ArrowUpRight size={18} weight="bold" />}
                    </span>
                    <span className="min-w-0 flex-1">
                      <span className="block truncate text-sm font-medium">{describeTransaction(t)}</span>
                      <span className="block truncate text-xs text-muted">
                        {t.account} · {formatDateTime(t.createdAt)}
                      </span>
                    </span>
                    <span className={`num text-sm font-medium ${credit ? 'text-ok' : ''}`}>
                      {credit ? '+' : '−'} {formatBRL(t.amount.amount)}
                    </span>
                  </li>
                )
              })}
            </ul>
          </section>

          {/* Contas */}
          <section className="card rise p-6" style={{ '--i': 8 } as React.CSSProperties}>
            <div className="mb-4 flex items-center justify-between">
              <h2 className="text-sm font-medium">Suas contas</h2>
              <Link to="/contas" className="text-sm font-medium text-accent hover:underline">
                Gerenciar
              </Link>
            </div>
            <ul className="flex flex-col gap-2">
              {list.map((a) => (
                <li key={a.id}>
                  <Link to={`/contas/${a.id}`} className="block rounded-xl border border-line bg-surface-2 px-4 py-3 transition hover:border-accent/50">
                    <span className="min-w-0">
                      <span className="block text-sm font-medium">{accountTypeLabel(a.type)}</span>
                      <span className="block truncate text-xs text-muted">{accountLabel(a)}</span>
                      <span className="num mt-1.5 block text-base font-semibold">{formatBRL(a.balance.amount)}</span>
                    </span>
                  </Link>
                </li>
              ))}
            </ul>
          </section>
        </div>
      )}
    </>
  )
}

function Delta({ value, goodWhen }: { value: number | null; goodWhen: 'up' | 'down' }) {
  if (value === null) return <span className="text-xs text-muted">sem base de comparação</span>
  const up = value >= 0
  const good = goodWhen === 'up' ? up : !up
  return (
    <span className={`inline-flex items-center gap-1 text-xs font-medium ${good ? 'text-ok' : 'text-danger'}`}>
      {up ? '▲' : '▼'} {Math.abs(value).toFixed(1).replace('.', ',')}% <span className="font-normal text-muted">que o mês passado</span>
    </span>
  )
}

function Stat({
  i,
  label,
  value,
  delta,
  goodWhen = 'up',
  hint,
  visual,
}: {
  i: number
  label: string
  value: string
  delta?: number | null
  goodWhen?: 'up' | 'down'
  hint?: string
  visual: React.ReactNode
}) {
  return (
    <section className="card rise p-5" style={{ '--i': i } as React.CSSProperties}>
      <div className="flex items-start justify-between gap-3">
        <p className="text-xs font-medium text-muted">{label}</p>
        {visual}
      </div>
      <p className="num mt-1 truncate text-2xl font-semibold tracking-tight">{value}</p>
      <div className="mt-1 min-h-4">{delta !== undefined ? <Delta value={delta} goodWhen={goodWhen} /> : <span className="text-xs text-muted">{hint}</span>}</div>
    </section>
  )
}

function Holding({ icon, label, value, percent, color, loading }: { icon: React.ReactNode; label: string; value: number; percent: number; color: string; loading?: boolean }) {
  return (
    <li className="flex items-center gap-3">
      <ProgressRing percent={percent} color={color}>
        <span style={{ color }}>{icon}</span>
      </ProgressRing>
      <span className="min-w-0 flex-1">
        <span className="block text-sm font-medium">{label}</span>
        <span className="num block text-xs text-muted">{loading ? '…' : brl(value)}</span>
      </span>
      <span className="text-xs text-muted">{loading ? '' : `${percent.toFixed(0)}%`}</span>
    </li>
  )
}
