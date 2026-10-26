import { useState, type ReactNode } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router'
import { AreaChart, Donut, ProgressRing } from '../../components/charts'
import { ErrorState, PageHeader, Panel, Select, Skeleton } from '../../components/ui'
import { staffApi, type Overview } from '../../services/staff'
import { messageFor } from '../../lib/errors'
import { compactBRL } from '../../lib/dashboard'
import { formatDateTime } from '../../lib/format'
import { formatBRL } from '../../lib/money'
import { attentionItems, dayLabel, percentOf, typeLabel, typeShares } from '../../lib/overview'

const number = new Intl.NumberFormat('pt-BR')

function Metric({ label, value, hint, tone }: { label: string; value: ReactNode; hint?: ReactNode; tone?: 'danger' }) {
  return (
    <div className="card overflow-hidden p-5">
      <p className="text-xs uppercase tracking-wider text-muted">{label}</p>
      <p className={`num mt-2 text-2xl font-semibold tracking-tight ${tone === 'danger' ? 'text-danger' : ''}`}>{value}</p>
      {hint && <p className="mt-1 text-xs text-muted">{hint}</p>}
    </div>
  )
}

/** Visão gerencial: números do banco agora e o movimento dos últimos dias. Só agregados, nenhum dado de cliente. */
export function OverviewPage() {
  const [days, setDays] = useState(30)
  const overview = useQuery({ queryKey: ['overview', days], queryFn: () => staffApi.overview(days), refetchInterval: 60_000 })

  return (
    <>
      <PageHeader
        title="Visão geral"
        description="Como o banco está agora: clientes, saldos, movimento, crédito e sinais de segurança."
        action={
          <Select aria-label="Período" value={days} onChange={(e) => setDays(Number(e.target.value))}>
            <option value={7}>Últimos 7 dias</option>
            <option value={30}>Últimos 30 dias</option>
            <option value={90}>Últimos 90 dias</option>
          </Select>
        }
      />
      {overview.isPending && <Skeleton className="h-96" />}
      {overview.isError && <ErrorState message={messageFor(overview.error)} onRetry={() => overview.refetch()} />}
      {overview.data && <Body days={days} data={overview.data} />}
    </>
  )
}

function Body({ days, data }: { days: number; data: Overview }) {
  const { customers, accounts, activity, credit, security } = data
  const labels = activity.days.map((d) => dayLabel(d.date))
  const shares = typeShares(activity.byType)
  const usedPct = percentOf(credit.used, credit.totalLimit)
  const alerts = security.failedLogins24h + security.accessDenied24h

  const attention = attentionItems(data)

  return (
    <div className="flex flex-col gap-6">
      <section aria-label="Atenção" className="card overflow-hidden p-5">
        <h2 className="text-sm font-semibold">Atenção agora</h2>
        {attention.length === 0 ? (
          <p className="mt-2 text-sm text-ok">Tudo em ordem: nenhuma fatura vencida, acesso negado ou fila parada.</p>
        ) : (
          <ul className="mt-3 flex flex-col gap-2">
            {attention.map((a) => (
              <li key={a.key}>
                <Link to={a.to} className={`flex items-center justify-between gap-3 rounded-ui px-3 py-2 text-sm transition hover:bg-surface-2 ${a.tone === 'danger' ? 'text-danger' : 'text-warn'}`}>
                  <span>{a.text}</span>
                  <span aria-hidden>→</span>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section aria-label="Resumo" className="grid gap-4 [grid-template-columns:repeat(auto-fit,minmax(13rem,1fr))]">
        <Metric label="Clientes" value={number.format(customers.total)} hint={`${number.format(customers.active)} ativos · ${number.format(customers.newInPeriod)} novos no período`} />
        <Metric label="Contas ativas" value={number.format(accounts.active)} hint={`${number.format(accounts.blocked)} bloqueadas · ${number.format(accounts.closed)} encerradas`} />
        <Metric label="Saldo sob custódia" value={formatBRL(accounts.totalBalance)} hint="Soma das contas em reais" />
        <Metric label="Movimento no período" value={formatBRL(activity.totalVolume)} hint={`${number.format(activity.totalCount)} operações de saída`} />
      </section>

      <div className="grid gap-6 lg:grid-cols-[2fr_1fr]">
        <Panel title={`Dinheiro que saiu por dia (${days} dias)`}>
          <AreaChart labels={labels} title="Volume diário de saídas" series={[{ name: 'Volume', color: 'var(--chart-1)', values: activity.days.map((d) => Number(d.volume)) }]} />
          <p className="mt-2 text-xs text-muted">Pico: {formatBRL(Math.max(0, ...activity.days.map((d) => Number(d.volume))))} em um dia.</p>
        </Panel>
        <Panel title="Por tipo de operação">
          {shares.length === 0 ? (
            <p className="text-sm text-muted">Nenhuma operação no período.</p>
          ) : (
            <>
              <Donut shares={shares} centerLabel="Volume" centerValue={`R$ ${compactBRL(Number(activity.totalVolume))}`} />
              <ul className="mt-4 flex flex-col gap-1.5 text-sm">
                {shares.map((s) => (
                  <li key={s.category} className="flex items-center justify-between gap-3">
                    <span className="flex items-center gap-2">
                      <span className="size-2.5 rounded-full" style={{ background: s.color }} aria-hidden />
                      {s.label}
                    </span>
                    <span className="num text-muted">{Math.round(s.percent)}%</span>
                  </li>
                ))}
              </ul>
            </>
          )}
        </Panel>
      </div>

      <div className="grid gap-6 lg:grid-cols-2">
        <Panel title="Cartão de crédito">
          <div className="flex items-center gap-5">
            <ProgressRing percent={usedPct} color={usedPct >= 80 ? 'var(--danger)' : 'var(--accent)'} size={72}>
              {usedPct}%
            </ProgressRing>
            <dl className="grid flex-1 gap-x-6 gap-y-2 text-sm sm:grid-cols-2">
              <div><dt className="text-muted">Cartões ativos</dt><dd className="num font-medium">{number.format(credit.cards)}</dd></div>
              <div><dt className="text-muted">Limite concedido</dt><dd className="num font-medium">{formatBRL(credit.totalLimit)}</dd></div>
              <div><dt className="text-muted">Dívida dos clientes</dt><dd className="num font-medium">{formatBRL(credit.used)}</dd></div>
              <div><dt className="text-muted">Multa e juros cobrados</dt><dd className="num font-medium">{formatBRL(credit.lateCharges)}</dd></div>
            </dl>
          </div>
          <p className={`mt-4 text-sm ${credit.overdueInvoices > 0 ? 'text-danger' : 'text-muted'}`}>
            {credit.overdueInvoices > 0
              ? `${number.format(credit.overdueInvoices)} fatura(s) vencida(s) com saldo, ${formatBRL(credit.overdueAmount)} em atraso.`
              : 'Nenhuma fatura vencida com saldo.'}
          </p>
        </Panel>

        <Panel title="Sinais de segurança e operação">
          <ul className="flex flex-col gap-3 text-sm">
            <li className="flex justify-between gap-3"><span className="text-muted">Logins recusados (24 h)</span><span className={`num font-medium ${security.failedLogins24h > 20 ? 'text-danger' : ''}`}>{number.format(security.failedLogins24h)}</span></li>
            <li className="flex justify-between gap-3"><span className="text-muted">Acessos negados (24 h)</span><span className={`num font-medium ${security.accessDenied24h > 0 ? 'text-danger' : ''}`}>{number.format(security.accessDenied24h)}</span></li>
            <li className="flex justify-between gap-3"><span className="text-muted">Eventos na fila de publicação</span><span className={`num font-medium ${security.outboxPending > 100 ? 'text-danger' : ''}`}>{number.format(security.outboxPending)}</span></li>
          </ul>
          <p className="mt-4 text-xs text-muted">{alerts === 0 ? 'Sem alertas nas últimas 24 horas.' : 'Veja os detalhes na Auditoria.'}</p>
        </Panel>
      </div>

      {activity.byType.length > 0 && (
        <Panel title="Detalhe por tipo">
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="text-xs uppercase tracking-wider text-muted">
                <tr><th className="py-2 pr-4 font-medium">Tipo</th><th className="py-2 pr-4 text-right font-medium">Operações</th><th className="py-2 text-right font-medium">Volume</th></tr>
              </thead>
              <tbody className="divide-y divide-line">
                {activity.byType.map((t) => (
                  <tr key={t.type}>
                    <td className="py-2 pr-4">{typeLabel(t.type)}</td>
                    <td className="num py-2 pr-4 text-right">{number.format(t.count)}</td>
                    <td className="num py-2 text-right">{formatBRL(t.volume)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Panel>
      )}
      <p className="text-xs text-muted">Atualizado em {formatDateTime(data.generatedAt)} · renova sozinho a cada minuto. Volume conta só o dinheiro que saiu (cada operação uma vez); câmbio em outras moedas não entra.</p>
    </div>
  )
}
