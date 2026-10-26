import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Lock } from '@phosphor-icons/react'
import { staffApi, type CardAdmin } from '../../services/staff'
import { useAuth } from '../../stores/auth'
import { formatDate } from '../../lib/format'
import { messageFor } from '../../lib/errors'
import { can, cardStatusLabel, reasonIssue } from '../../lib/staff'
import { Alert, Badge, Button, ErrorState, Field, Input, Panel, Skeleton } from '../../components/ui'
import { Dialog } from '../../components/Dialog'

/** Todos os cartões do cliente (sem número). Quem tem a alçada de Risco bloqueia, com motivo, e desbloqueia; o cliente é avisado. */
export function CardsPanel({ customerId }: { customerId: string }) {
  const isAdmin = useAuth((s) => can(s.claims?.roles ?? [], 'MANAGE_ACCOUNTS'))
  const client = useQueryClient()
  const cards = useQuery({ queryKey: ['staff', 'cards', customerId], queryFn: () => staffApi.cards(customerId) })
  const [blocking, setBlocking] = useState<CardAdmin | null>(null)
  const refresh = () => Promise.all([client.invalidateQueries({ queryKey: ['staff', 'cards', customerId] }), client.invalidateQueries({ queryKey: ['staff', 'credit-cards', customerId] })])
  const unblock = useMutation({ mutationFn: (id: string) => staffApi.unblockCard(id), onSuccess: refresh })

  return (
    <Panel title="Cartões">
      {cards.isPending && <Skeleton className="h-16" />}
      {cards.isError && <ErrorState message={messageFor(cards.error)} onRetry={() => cards.refetch()} />}
      {cards.data?.length === 0 && <p className="text-sm text-muted">Este cliente não tem cartões ativos.</p>}
      {unblock.isError && <Alert tone="error">{messageFor(unblock.error)}</Alert>}
      {!!cards.data?.length && (
        <ul className="divide-y divide-line">
          {cards.data.map((c) => (
            <li key={c.id} className="flex flex-wrap items-center justify-between gap-3 py-3 first:pt-0 last:pb-0">
              <span className="min-w-0">
                <span className="block font-medium">
                  {c.kind === 'CREDIT' ? 'Crédito' : 'Débito'} final {c.last4} <span className="text-sm font-normal text-muted">· {c.holderName}</span>
                </span>
                <span className="block text-sm text-muted">
                  Emitido em {formatDate(c.createdAt)}
                  {c.status === 'BLOCKED' && c.statusReason ? ` · motivo: ${c.statusReason}` : ''}
                </span>
              </span>
              <span className="flex items-center gap-3">
                <Badge tone={c.status === 'ACTIVE' ? 'ok' : 'warn'}>{cardStatusLabel(c.status)}</Badge>
                {isAdmin && c.status === 'ACTIVE' && (
                  <Button variant="secondary" onClick={() => setBlocking(c)}>
                    Bloquear
                  </Button>
                )}
                {isAdmin && c.status === 'BLOCKED' && (
                  <Button variant="secondary" loading={unblock.isPending && unblock.variables === c.id} onClick={() => unblock.mutate(c.id)}>
                    Desbloquear
                  </Button>
                )}
              </span>
            </li>
          ))}
        </ul>
      )}
      <BlockDialog card={blocking} onClose={() => setBlocking(null)} onDone={async () => { setBlocking(null); await refresh() }} />
    </Panel>
  )
}

function BlockDialog({ card, onClose, onDone }: { card: CardAdmin | null; onClose: () => void; onDone: () => unknown }) {
  return (
    <Dialog open={card !== null} onClose={onClose} title={card ? `Bloquear cartão final ${card.last4}` : ''} icon={<Lock size={22} weight="duotone" />}>
      {card && <BlockBody key={card.id} card={card} onClose={onClose} onDone={onDone} />}
    </Dialog>
  )
}

function BlockBody({ card, onClose, onDone }: { card: CardAdmin; onClose: () => void; onDone: () => unknown }) {
  const [reason, setReason] = useState('')
  const [issue, setIssue] = useState<string | null>(null)
  const block = useMutation({ mutationFn: () => staffApi.blockCard(card.id, reason.trim()), onSuccess: () => onDone() })

  function submit() {
    const problem = reasonIssue(reason)
    setIssue(problem)
    if (!problem) block.mutate()
  }

  return (
    <form className="flex flex-col gap-4" onSubmit={(e) => { e.preventDefault(); submit() }} noValidate>
      <p className="text-sm text-muted">
        O cartão para de aceitar compras na hora e o cliente é avisado. O motivo fica no cartão e na auditoria, marcado como feito pela equipe.
      </p>
      {block.isError && <Alert tone="error">{messageFor(block.error)}</Alert>}
      <Field label="Motivo" htmlFor="block-reason" error={issue ?? undefined} hint="De 3 a 140 caracteres. Ex.: suspeita de fraude">
        <Input id="block-reason" value={reason} maxLength={140} autoComplete="off" aria-invalid={!!issue} onChange={(e) => { setReason(e.target.value); setIssue(null) }} />
      </Field>
      <div className="-mx-6 -mb-4 flex flex-wrap justify-end gap-3 border-t border-line bg-surface-2/60 px-6 py-4">
        <Button type="button" variant="secondary" onClick={onClose} disabled={block.isPending}>Voltar</Button>
        <Button type="submit" loading={block.isPending}>Bloquear cartão</Button>
      </div>
    </form>
  )
}
