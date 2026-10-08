import { useState } from 'react'
import { ArrowDownLeft, ArrowUpRight } from '@phosphor-icons/react'
import { ApiError } from '../../lib/api'
import { createIdempotency } from '../../lib/idempotency'
import { formatDateTime } from '../../lib/format'
import { messageFor } from '../../lib/errors'
import { formatBRL, parseAmount } from '../../lib/money'
import { refundAmountError } from '../../lib/pix'
import { pixApi, type PixEntry } from '../../services/pix'
import { Alert, Badge, Button, EmptyState, ErrorState, Field, Input, PageHeader, Skeleton } from '../../components/ui'
import { usePixHistory, useRefreshPix } from './hooks'

const title = (e: PixEntry) => {
  const sent = e.direction === 'SENT'
  if (e.refundOfId) return sent ? `Devolução enviada para ${e.counterpartName}` : `Devolução recebida de ${e.counterpartName}`
  return sent ? `Pix enviado para ${e.counterpartName}` : `Pix recebido de ${e.counterpartName}`
}

const canRefund = (e: PixEntry) => e.direction === 'RECEIVED' && !e.refundOfId && Number(e.refundableAmount?.amount ?? 0) > 0

/** Histórico dos Pix enviados e recebidos, com a devolução (total ou parcial, em até 90 dias) nos recebidos. */
export function PixHistoryPage() {
  const [page, setPage] = useState(0)
  const history = usePixHistory(page, 10)
  const [refunding, setRefunding] = useState<string | null>(null)
  const pages = history.data ? Math.max(1, Math.ceil(history.data.totalElements / history.data.size)) : 1

  return (
    <>
      <PageHeader title="Histórico do Pix" description="Pix enviados e recebidos, com a contraparte mascarada." />
      {history.isPending && <Skeleton className="h-64" />}
      {history.isError && <ErrorState message={messageFor(history.error)} onRetry={() => history.refetch()} />}
      {history.data?.items.length === 0 && <EmptyState title="Nenhum Pix ainda">Os Pix que você enviar e receber aparecem aqui.</EmptyState>}
      {!!history.data?.items.length && (
        <>
          <ul className="card divide-y divide-line overflow-hidden">
            {history.data.items.map((e) => {
              const received = e.direction === 'RECEIVED'
              return (
                <li key={e.id} className="px-5 py-4">
                  <div className="flex items-center gap-3">
                    <span className="grid size-10 shrink-0 place-items-center rounded-xl bg-surface-2" style={{ color: received ? 'var(--chart-2)' : 'var(--chart-1)' }}>
                      {received ? <ArrowDownLeft size={18} weight="bold" /> : <ArrowUpRight size={18} weight="bold" />}
                    </span>
                    <span className="min-w-0 flex-1">
                      <span className="block break-words text-sm font-medium">{title(e)}</span>
                      <span className="block truncate text-xs text-muted">{[formatDateTime(e.createdAt), e.message].filter(Boolean).join(' · ')}</span>
                    </span>
                    <span className="text-right">
                      <span className={`num block text-sm font-medium ${received ? 'text-ok' : ''}`}>{received ? '+' : '−'} {formatBRL(e.amount.amount)}</span>
                      {Number(e.refundedAmount?.amount ?? 0) > 0 && <Badge tone="neutral">Devolvido {formatBRL(e.refundedAmount!.amount)}</Badge>}
                    </span>
                  </div>
                  {canRefund(e) && refunding !== e.id && (
                    <div className="mt-2 pl-[3.25rem]">
                      <Button variant="ghost" onClick={() => setRefunding(e.id)}>Devolver (até {formatBRL(e.refundableAmount!.amount)})</Button>
                    </div>
                  )}
                  {refunding === e.id && <Refund entry={e} onClose={() => setRefunding(null)} />}
                </li>
              )
            })}
          </ul>
          <div className="mt-4 flex items-center justify-between text-sm text-muted">
            <span>{history.data.totalElements} Pix · página {page + 1} de {pages}</span>
            <span className="flex gap-2">
              <Button variant="secondary" disabled={page === 0} onClick={() => setPage(page - 1)}>Anterior</Button>
              <Button variant="secondary" disabled={page + 1 >= pages} onClick={() => setPage(page + 1)}>Próxima</Button>
            </span>
          </div>
        </>
      )}
    </>
  )
}

function Refund({ entry, onClose }: { entry: PixEntry; onClose: () => void }) {
  const refresh = useRefreshPix()
  const [idem] = useState(() => createIdempotency())
  const remaining = entry.refundableAmount!.amount
  const [amount, setAmount] = useState(remaining.replace('.', ','))
  const [error, setError] = useState<string | null>(null)
  const [done, setDone] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const fieldError = refundAmountError(amount, remaining)

  async function confirm() {
    if (fieldError) return
    setSubmitting(true)
    setError(null)
    const value = parseAmount(amount)!
    try {
      await pixApi.refund(entry.id, value, idem.keyFor({ id: entry.id, value }))
      idem.settle()
      setDone(true)
      await refresh()
    } catch (e) {
      if (!(e instanceof ApiError) || !e.uncertain) idem.settle()
      setError(messageFor(e))
    } finally {
      setSubmitting(false)
    }
  }

  if (done) {
    return (
      <div className="mt-3 pl-[3.25rem]">
        <Alert tone="success">Devolução enviada para {entry.counterpartName}.</Alert>
        <Button variant="ghost" className="mt-2" onClick={onClose}>Fechar</Button>
      </div>
    )
  }
  return (
    <div className="mt-3 flex flex-col gap-3 pl-[3.25rem]">
      <Field label="Valor a devolver (R$)" htmlFor={`refund-${entry.id}`} error={fieldError ?? undefined} hint={`Ainda pode devolver ${formatBRL(remaining)} em até 90 dias do recebimento.`}>
        <Input id={`refund-${entry.id}`} inputMode="decimal" className="num max-w-48" value={amount} aria-invalid={!!fieldError} onChange={(ev) => setAmount(ev.target.value)} />
      </Field>
      {error && <Alert tone="error">{error}</Alert>}
      <div className="flex gap-2">
        <Button loading={submitting} disabled={!!fieldError} onClick={confirm}>Devolver</Button>
        <Button variant="secondary" disabled={submitting} onClick={onClose}>Cancelar</Button>
      </div>
    </div>
  )
}
