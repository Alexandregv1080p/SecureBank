import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { staffApi, type CreditCardAdmin } from '../../services/staff'
import { useAuth } from '../../stores/auth'
import { formatBRL, parseAmount } from '../../lib/money'
import { messageFor } from '../../lib/errors'
import { cardStatusLabel, creditLimitIssue } from '../../lib/staff'
import { sanitizeAmount } from '../../lib/validation'
import { Alert, Badge, Button, ErrorState, Field, Input, Panel, Skeleton } from '../../components/ui'

/** Cartões de crédito do cliente: limite, uso e vencimento. O ADMIN decide o limite (até R$ 100.000,00, nunca abaixo da dívida). */
export function CreditCardsPanel({ customerId }: { customerId: string }) {
  const cards = useQuery({ queryKey: ['staff', 'credit-cards', customerId], queryFn: () => staffApi.creditCards(customerId) })
  return (
    <Panel title="Cartões de crédito">
      {cards.isPending && <Skeleton className="h-16" />}
      {cards.isError && <ErrorState message={messageFor(cards.error)} onRetry={() => cards.refetch()} />}
      {cards.data?.length === 0 && <p className="text-sm text-muted">Este cliente não tem cartão de crédito.</p>}
      {!!cards.data?.length && (
        <ul className="divide-y divide-line">
          {cards.data.map((c) => (
            <CardRow key={c.id} card={c} customerId={customerId} />
          ))}
        </ul>
      )}
    </Panel>
  )
}

function CardRow({ card, customerId }: { card: CreditCardAdmin; customerId: string }) {
  const isAdmin = useAuth((s) => s.claims?.roles.includes('ADMIN'))
  const client = useQueryClient()
  const [value, setValue] = useState('')
  const [issue, setIssue] = useState<string | null>(null)
  const [done, setDone] = useState<string | null>(null)
  const change = useMutation({
    mutationFn: (limit: string) => staffApi.setCreditLimit(card.id, limit),
    onSuccess: async (r) => {
      setDone(`Novo limite: ${formatBRL(r.creditLimit.amount)}. O cliente foi avisado.`)
      setValue('')
      await client.invalidateQueries({ queryKey: ['staff', 'credit-cards', customerId] })
    },
  })

  function submit() {
    const problem = creditLimitIssue(value, card.used.amount)
    setIssue(problem)
    setDone(null)
    if (!problem) change.mutate(parseAmount(value)!)
  }

  return (
    <li className="flex flex-col gap-3 py-4 first:pt-0 last:pb-0">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <span>
          <span className="block font-medium">Final {card.last4}</span>
          <span className="block text-sm text-muted">Vence todo dia {card.dueDay}</span>
        </span>
        <Badge tone={card.status === 'ACTIVE' ? 'ok' : card.status === 'BLOCKED' ? 'warn' : 'danger'}>{cardStatusLabel(card.status)}</Badge>
      </div>
      <dl className="grid gap-x-6 gap-y-1 text-sm sm:grid-cols-3">
        <div><dt className="text-muted">Limite</dt><dd className="num font-medium">{formatBRL(card.creditLimit.amount)}</dd></div>
        <div><dt className="text-muted">Em uso</dt><dd className="num font-medium">{formatBRL(card.used.amount)}</dd></div>
        <div><dt className="text-muted">Disponível</dt><dd className="num font-medium">{formatBRL(card.available.amount)}</dd></div>
      </dl>
      {isAdmin && card.status !== 'CANCELED' && (
        <form className="flex flex-col gap-3 sm:flex-row sm:items-start" onSubmit={(e) => { e.preventDefault(); submit() }} noValidate>
          <div className="flex-1">
            <Field label="Novo limite (R$)" htmlFor={`limit-${card.id}`} error={issue ?? undefined} hint="Múltiplos de R$ 100,00, até R$ 100.000,00">
              <Input id={`limit-${card.id}`} inputMode="decimal" className="num" placeholder="0,00" autoComplete="off" value={value} aria-invalid={!!issue}
                onChange={(e) => { setValue(sanitizeAmount(e.target.value)); setIssue(null) }} />
            </Field>
          </div>
          <Button type="submit" loading={change.isPending} className="sm:mt-7">Alterar limite</Button>
        </form>
      )}
      {change.isError && <Alert tone="error">{messageFor(change.error)}</Alert>}
      {done && <Alert tone="success">{done}</Alert>}
    </li>
  )
}
