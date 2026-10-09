import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router'
import { CaretLeft } from '@phosphor-icons/react'
import { investmentApi } from '../../services/investments'
import { ApiError } from '../../lib/api'
import { createIdempotency } from '../../lib/idempotency'
import { formatDate, formatDateTime } from '../../lib/format'
import { messageFor } from '../../lib/errors'
import { formatBRL, parseAmount } from '../../lib/money'
import { lockedMessage, rateLabel, redeemError, termLabel } from '../../lib/investment'
import { sanitizeAmount } from '../../lib/validation'
import { Alert, Badge, Button, ErrorState, Field, Input, Panel, Skeleton } from '../../components/ui'
import { useRefreshInvestments } from './hooks'

/** Uma aplicação: valor de hoje (bruto, rendimento, IR, líquido) e o resgate, total ou de um valor líquido. */
export function InvestmentDetailPage() {
  const { id = '' } = useParams()
  const investment = useQuery({ queryKey: ['investment', id], queryFn: () => investmentApi.get(id) })
  const refresh = useRefreshInvestments()
  const [idem] = useState(() => createIdempotency())
  const [amount, setAmount] = useState('')
  const [fieldError, setFieldError] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [done, setDone] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  if (investment.isPending) return <Skeleton className="h-64" />
  if (investment.isError) return <ErrorState message={messageFor(investment.error)} onRetry={() => investment.refetch()} />
  const i = investment.data
  const active = i.status === 'ACTIVE'

  async function redeem() {
    const problem = redeemError(amount)
    setFieldError(problem)
    if (problem) return
    setError(null)
    setDone(null)
    setSubmitting(true)
    const value = amount.trim() ? parseAmount(amount)! : undefined
    try {
      const updated = await investmentApi.redeem(id, value, idem.keyFor({ id, value }))
      idem.settle()
      setAmount('')
      setDone(updated.paidAmount ? `${formatBRL(updated.paidAmount.amount)} resgatados. O valor já está na sua conta.` : 'Resgate realizado.')
      await refresh()
    } catch (e) {
      if (!(e instanceof ApiError) || !e.uncertain) idem.settle()
      setError(messageFor(e))
    } finally {
      setSubmitting(false)
    }
  }

  const lines: [string, string][] = [
    ['Aplicado', formatBRL(i.principal.amount)],
    ['Aplicado em', formatDateTime(i.appliedAt)],
    ...(i.maturesAt ? ([['Vencimento', formatDate(i.maturesAt)]] as [string, string][]) : []),
    ['Dias rendendo', String(i.daysHeld)],
    ['Valor bruto', formatBRL(i.gross.amount)],
    ['Rendimento', formatBRL(i.yield.amount)],
    [`IR (${i.taxRatePercent.replace('.', ',')}%)`, `− ${formatBRL(i.tax.amount)}`],
  ]

  return (
    <>
      <Link to="/investimentos" className="mb-4 inline-flex items-center gap-1 text-sm text-muted hover:text-ink">
        <CaretLeft size={14} /> Investimentos
      </Link>

      <section className="card-hero rise mb-8 p-6">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <h1 className="text-2xl font-semibold tracking-tight">{i.productName}</h1>
            <p className="mt-1 text-sm text-muted">{rateLabel(i.annualRatePercent)} · {termLabel(i.termDays)}</p>
          </div>
          {!active && <Badge>Resgatado</Badge>}
          {active && i.termDays !== null && (i.canRedeem ? <Badge tone="ok">Disponível para resgate</Badge> : <Badge tone="warn">Aguardando vencimento</Badge>)}
        </div>
        <p className="mt-5 text-sm text-muted">{active ? 'Líquido se resgatar agora' : 'Líquido resgatado'}</p>
        <p className="num text-4xl font-semibold tracking-tight">{formatBRL(i.net.amount)}</p>
      </section>

      <div className="grid gap-6 lg:grid-cols-2">
        <Panel title="Valores de hoje">
          <dl className="flex flex-col gap-3 text-sm">
            {lines.map(([label, value]) => (
              <div key={label} className="flex justify-between gap-6">
                <dt className="text-muted">{label}</dt>
                <dd className="num">{value}</dd>
              </div>
            ))}
            <div className="flex justify-between gap-6 border-t border-line pt-3 font-medium">
              <dt>Líquido</dt>
              <dd className="num">{formatBRL(i.net.amount)}</dd>
            </div>
          </dl>
        </Panel>

        {active && (
          <Panel title="Resgatar">
            {!i.canRedeem ? (
              <p className="text-sm text-muted">{lockedMessage(i.maturesAt, formatDate)}</p>
            ) : (
              <form className="flex flex-col gap-4" onSubmit={(e) => { e.preventDefault(); void redeem() }} noValidate>
                {error && <Alert tone="error">{error}</Alert>}
                {done && <Alert tone="success">{done}</Alert>}
                <Field label="Valor a resgatar (R$)" htmlFor="redeem" error={fieldError ?? undefined} hint="Líquido, já descontado o IR. Deixe em branco para resgatar tudo; o que sobrar continua rendendo.">
                  <Input id="redeem" inputMode="decimal" className="num" placeholder="0,00" autoComplete="off" value={amount} aria-invalid={!!fieldError} onChange={(e) => { setAmount(sanitizeAmount(e.target.value)); setFieldError(null) }} />
                </Field>
                <div><Button type="submit" loading={submitting}>{amount.trim() ? 'Resgatar este valor' : 'Resgatar tudo'}</Button></div>
              </form>
            )}
          </Panel>
        )}
      </div>
    </>
  )
}
