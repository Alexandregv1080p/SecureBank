import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router'
import { XCircle } from '@phosphor-icons/react'
import { staffApi, type Approval } from '../../services/staff'
import { approvalKindLabel, approvalStatusLabel, departmentLabel, reasonIssue } from '../../lib/staff'
import { formatDateTime } from '../../lib/format'
import { formatBRL } from '../../lib/money'
import { messageFor } from '../../lib/errors'
import { Alert, Badge, Button, EmptyState, ErrorState, Field, Input, PageHeader, Skeleton } from '../../components/ui'
import { ConfirmButton } from '../../components/ConfirmButton'
import { Dialog } from '../../components/Dialog'

type Filter = 'PENDING' | 'ALL'

const statusTone = (status: Approval['status']) => (status === 'APPROVED' ? 'ok' : status === 'PENDING' ? 'warn' : status === 'REJECTED' ? 'danger' : 'neutral')

/** Fila de pedidos que passaram da alçada de quem pediu: o gerente do departamento (ou a Diretoria) aprova ou recusa com motivo. */
export function ApprovalsPage() {
  const [filter, setFilter] = useState<Filter>('PENDING')
  const [rejecting, setRejecting] = useState<Approval | null>(null)
  const client = useQueryClient()
  const list = useQuery({ queryKey: ['staff', 'approvals', filter], queryFn: () => staffApi.approvals(filter === 'PENDING' ? 'PENDING' : undefined) })
  const refresh = () => client.invalidateQueries({ queryKey: ['staff', 'approvals'] })
  const approve = useMutation({ mutationFn: (a: Approval) => staffApi.approve(a.id), onSuccess: refresh })
  const cancel = useMutation({ mutationFn: (a: Approval) => staffApi.cancelApproval(a.id), onSuccess: refresh })

  return (
    <>
      <PageHeader
        title="Aprovações"
        description="Pedidos que passaram da alçada de quem pediu. Quem decide é um gerente do departamento (ou a Diretoria), nunca quem fez o pedido."
      />
      <div className="mb-6 inline-flex rounded-ui border border-line bg-surface p-1" role="group" aria-label="Filtro">
        {([['PENDING', 'Aguardando'], ['ALL', 'Todos']] as const).map(([value, label]) => (
          <button
            key={value}
            type="button"
            aria-pressed={filter === value}
            onClick={() => setFilter(value)}
            className={`rounded-ui px-4 py-1.5 text-sm font-medium transition ${filter === value ? 'bg-accent-soft text-ink' : 'text-muted hover:text-ink'}`}
          >
            {label}
          </button>
        ))}
      </div>

      {(approve.isError || cancel.isError) && (
        <div className="mb-4">
          <Alert tone="error">{messageFor(approve.error ?? cancel.error)}</Alert>
        </div>
      )}
      {list.isPending && <Skeleton className="h-40" />}
      {list.isError && <ErrorState message={messageFor(list.error)} onRetry={() => list.refetch()} />}
      {list.data?.length === 0 && (
        <EmptyState title={filter === 'PENDING' ? 'Nada aguardando' : 'Nenhum pedido ainda'}>
          {filter === 'PENDING' ? 'Quando alguém pedir algo acima da própria alçada, aparece aqui.' : 'Os pedidos feitos e decididos ficam registrados aqui.'}
        </EmptyState>
      )}
      {!!list.data?.length && (
        <ul className="flex flex-col gap-4">
          {list.data.map((a) => (
            <li key={a.id} className="card flex flex-col gap-3 p-5">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div className="min-w-0">
                  <p className="font-medium">
                    {approvalKindLabel(a.kind)}
                    {a.amount && <span className="num"> · {formatBRL(a.amount)}</span>}
                  </p>
                  <p className="text-sm text-muted">
                    {a.subject}
                    {a.currentLimit && a.status === 'PENDING' && <>, limite atual <span className="num">{formatBRL(a.currentLimit)}</span></>}
                  </p>
                </div>
                <Badge tone={statusTone(a.status)}>{approvalStatusLabel(a.status)}</Badge>
              </div>
              <dl className="grid gap-x-6 gap-y-1 text-sm sm:grid-cols-2">
                <div><dt className="text-muted">Pedido por</dt><dd className="break-all font-medium">{a.requestedBy}</dd></div>
                <div><dt className="text-muted">Quem decide</dt><dd className="font-medium">Gerente de {departmentLabel(a.department)} ou Diretoria</dd></div>
                <div><dt className="text-muted">Em</dt><dd className="font-medium">{formatDateTime(a.createdAt)}</dd></div>
                <div>
                  <dt className="text-muted">Cliente</dt>
                  <dd><Link className="font-medium text-accent hover:underline" to={`/clientes?id=${a.customerId}`}>Abrir ficha</Link></dd>
                </div>
              </dl>
              {a.note && <p className="rounded-ui bg-surface-2 px-3 py-2 text-sm"><span className="text-muted">Motivo do pedido: </span>{a.note}</p>}
              {a.decidedBy && (
                <p className="text-sm text-muted">
                  {approvalStatusLabel(a.status)} por <span className="break-all font-medium text-ink">{a.decidedBy}</span>
                  {a.decidedAt && <> em {formatDateTime(a.decidedAt)}</>}
                  {a.decisionNote && <>: {a.decisionNote}</>}
                </p>
              )}
              {(a.canDecide || a.canCancel) && (
                <div className="flex flex-wrap items-center gap-3">
                  {a.canDecide && (
                    <>
                      <ConfirmButton label="Aprovar" confirmLabel="Confirmar aprovação" variant="secondary" loading={approve.isPending && approve.variables?.id === a.id} onConfirm={() => approve.mutate(a)} />
                      <Button variant="danger" onClick={() => setRejecting(a)}>Recusar</Button>
                    </>
                  )}
                  {a.canCancel && (
                    <ConfirmButton label="Cancelar pedido" confirmLabel="Confirmar cancelamento" variant="secondary" loading={cancel.isPending && cancel.variables?.id === a.id} onConfirm={() => cancel.mutate(a)} />
                  )}
                </div>
              )}
            </li>
          ))}
        </ul>
      )}

      <Dialog open={rejecting !== null} onClose={() => setRejecting(null)} title="Recusar pedido" icon={<XCircle size={22} weight="duotone" />}>
        {rejecting && <RejectBody key={rejecting.id} approval={rejecting} onClose={() => setRejecting(null)} onDone={refresh} />}
      </Dialog>
    </>
  )
}

function RejectBody({ approval, onClose, onDone }: { approval: Approval; onClose: () => void; onDone: () => unknown }) {
  const [reason, setReason] = useState('')
  const [issue, setIssue] = useState<string | null>(null)
  const reject = useMutation({
    mutationFn: () => staffApi.reject(approval.id, reason.trim()),
    onSuccess: async () => {
      await onDone()
      onClose()
    },
  })

  function submit() {
    const problem = reasonIssue(reason)
    setIssue(problem)
    if (!problem) reject.mutate()
  }

  return (
    <form className="flex flex-col gap-4" onSubmit={(e) => { e.preventDefault(); submit() }} noValidate>
      <p className="text-sm text-muted">
        {approvalKindLabel(approval.kind)}: {approval.subject}{approval.amount && <>, para <span className="num">{formatBRL(approval.amount)}</span></>}. Quem pediu vê o motivo da recusa e nada muda.
      </p>
      {reject.isError && <Alert tone="error">{messageFor(reject.error)}</Alert>}
      <Field label="Motivo da recusa" htmlFor="reject-reason" error={issue ?? undefined} hint="De 3 a 140 caracteres.">
        <Input id="reject-reason" value={reason} maxLength={140} autoComplete="off" aria-invalid={!!issue} onChange={(e) => { setReason(e.target.value); setIssue(null) }} />
      </Field>
      <div className="-mx-6 -mb-4 flex flex-wrap justify-end gap-3 border-t border-line bg-surface-2/60 px-6 py-4">
        <Button type="button" variant="secondary" onClick={onClose} disabled={reject.isPending}>Voltar</Button>
        <Button type="submit" variant="danger" loading={reject.isPending}>Recusar pedido</Button>
      </div>
    </form>
  )
}
