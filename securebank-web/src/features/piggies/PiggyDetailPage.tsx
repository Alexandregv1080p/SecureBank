import { useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { Link, useNavigate, useParams } from 'react-router'
import { CaretLeft } from '@phosphor-icons/react'
import { piggyApi } from '../../services/piggies'
import { ApiError } from '../../lib/api'
import { createIdempotency } from '../../lib/idempotency'
import { messageFor } from '../../lib/errors'
import { formatBRL, parseAmount } from '../../lib/money'
import { goalError, moveError, nameError, NAME_MAX, progress, remainingToGoal } from '../../lib/piggy'
import { sanitizeAmount } from '../../lib/validation'
import { Alert, Badge, Button, ErrorState, Field, Input, Panel, Skeleton } from '../../components/ui'
import { ConfirmButton } from '../../components/ConfirmButton'
import { useAccounts } from '../accounts/hooks'
import { useRefreshPiggies } from './hooks'

type Tab = 'save' | 'redeem'

/** Um porquinho: saldo e meta, guardar/resgatar, editar nome e meta, e fechar (o que houver volta para a conta). */
export function PiggyDetailPage() {
  const { id = '' } = useParams()
  const navigate = useNavigate()
  const accounts = useAccounts()
  const refresh = useRefreshPiggies()
  const piggy = useQuery({ queryKey: ['piggy', id], queryFn: () => piggyApi.get(id) })
  const [tab, setTab] = useState<Tab>('save')
  const close = useMutation({ mutationFn: () => piggyApi.close(id), onSuccess: async () => { await refresh(); navigate('/porquinhos', { replace: true }) } })

  if (piggy.isPending) return <Skeleton className="h-64" />
  if (piggy.isError) return <ErrorState message={messageFor(piggy.error)} onRetry={() => piggy.refetch()} />
  const p = piggy.data
  const account = accounts.data?.find((a) => a.id === p.accountId)
  const pct = p.progressPercent ?? progress(p.balance.amount, p.goal?.amount ?? null)
  const left = remainingToGoal(p.balance.amount, p.goal?.amount ?? null)

  return (
    <>
      <Link to="/porquinhos" className="mb-4 inline-flex items-center gap-1 text-sm text-muted hover:text-ink">
        <CaretLeft size={14} /> Porquinhos
      </Link>

      <section className="card-hero rise mb-8 p-6">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <h1 className="text-2xl font-semibold tracking-tight">{p.name}</h1>
            <p className="mt-1 text-sm text-muted">Guardado</p>
          </div>
          {p.goalReached && <Badge tone="ok">Meta alcançada</Badge>}
        </div>
        <p className="num mt-1 text-4xl font-semibold tracking-tight">{formatBRL(p.balance.amount)}</p>
        {p.goal && pct !== null ? (
          <div className="mt-5">
            <div className="h-2.5 overflow-hidden rounded-full bg-surface-2" role="progressbar" aria-valuenow={pct} aria-valuemin={0} aria-valuemax={100} aria-label="Progresso da meta">
              <div className="h-full rounded-full bg-accent transition-all" style={{ width: `${pct}%` }} />
            </div>
            <p className="mt-2 text-sm text-muted">
              {pct}% de {formatBRL(p.goal.amount)}
              {left && Number(left) > 0 ? ` · faltam ${formatBRL(left)}` : ''}
            </p>
          </div>
        ) : (
          <p className="mt-3 text-sm text-muted">Sem meta definida.</p>
        )}
      </section>

      <div className="grid gap-6 lg:grid-cols-2">
        <Panel title="Movimentar">
          <div role="tablist" aria-label="Movimentação" className="mb-5 flex gap-1 rounded-ui bg-surface-2 p-1">
            {(['save', 'redeem'] as const).map((k) => (
              <button
                key={k}
                role="tab"
                aria-selected={tab === k}
                onClick={() => setTab(k)}
                className={`flex-1 rounded-ui px-3 py-2 text-sm font-medium transition ${tab === k ? 'bg-accent text-accent-fg' : 'text-muted hover:text-ink'}`}
              >
                {k === 'save' ? 'Guardar' : 'Resgatar'}
              </button>
            ))}
          </div>
          <MoveForm key={tab} piggyId={id} kind={tab} available={tab === 'save' ? account?.balance.amount : p.balance.amount} availableLabel={tab === 'save' ? 'Saldo da conta' : 'Guardado no porquinho'} onDone={refresh} />
        </Panel>

        <div className="flex flex-col gap-6">
          <Panel title="Editar">
            <EditForm key={`${p.name}-${p.goal?.amount ?? ''}`} piggyId={id} name={p.name} goal={p.goal?.amount ?? null} onDone={refresh} />
          </Panel>
          <Panel title="Fechar porquinho">
            <p className="text-sm text-muted">
              {Number(p.balance.amount) > 0 ? `Os ${formatBRL(p.balance.amount)} guardados voltam para a sua conta.` : 'Ele está vazio, então é só fechar.'} Esta ação não pode ser desfeita.
            </p>
            {close.isError && <div className="mt-3"><Alert tone="error">{messageFor(close.error)}</Alert></div>}
            <div className="mt-4">
              <ConfirmButton label="Fechar porquinho" confirmLabel="Confirmar e fechar" loading={close.isPending} onConfirm={() => close.mutate()} />
            </div>
          </Panel>
        </div>
      </div>
    </>
  )
}

function MoveForm({ piggyId, kind, available, availableLabel, onDone }: { piggyId: string; kind: Tab; available?: string; availableLabel: string; onDone: () => unknown }) {
  const [idem] = useState(() => createIdempotency())
  const [amount, setAmount] = useState('')
  const [fieldError, setFieldError] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [done, setDone] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function submit() {
    const problem = moveError(amount, available)
    setFieldError(problem)
    if (problem) return
    setError(null)
    setDone(null)
    setSubmitting(true)
    const value = parseAmount(amount)!
    try {
      const key = idem.keyFor({ piggyId, kind, value })
      await (kind === 'save' ? piggyApi.save : piggyApi.redeem)(piggyId, value, key)
      idem.settle()
      setAmount('')
      setDone(kind === 'save' ? `${formatBRL(value)} guardados.` : `${formatBRL(value)} voltaram para a sua conta.`)
      await onDone()
    } catch (e) {
      if (!(e instanceof ApiError) || !e.uncertain) idem.settle()
      setError(messageFor(e))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <form className="flex flex-col gap-4" onSubmit={(e) => { e.preventDefault(); void submit() }} noValidate>
      {error && <Alert tone="error">{error}</Alert>}
      {done && <Alert tone="success">{done}</Alert>}
      <Field
        label={kind === 'save' ? 'Valor a guardar (R$)' : 'Valor a resgatar (R$)'}
        htmlFor={`move-${kind}`}
        error={fieldError ?? undefined}
        hint={available !== undefined ? `${availableLabel}: ${formatBRL(available)}` : undefined}
      >
        <Input id={`move-${kind}`} inputMode="decimal" className="num" placeholder="0,00" autoComplete="off" value={amount} aria-invalid={!!fieldError} onChange={(e) => { setAmount(sanitizeAmount(e.target.value)); setFieldError(null) }} />
      </Field>
      <div><Button type="submit" loading={submitting}>{kind === 'save' ? 'Guardar' : 'Resgatar'}</Button></div>
    </form>
  )
}

function EditForm({ piggyId, name, goal, onDone }: { piggyId: string; name: string; goal: string | null; onDone: () => unknown }) {
  const [newName, setNewName] = useState(name)
  const [newGoal, setNewGoal] = useState(goal ? goal.replace('.', ',') : '')
  const [errors, setErrors] = useState<Record<string, string>>({})
  const save = useMutation({
    mutationFn: () => {
      const body: { name?: string; goal?: string; clearGoal?: boolean } = {}
      if (newName.trim() !== name) body.name = newName.trim()
      if (!newGoal.trim()) {
        if (goal !== null) body.clearGoal = true
      } else if (parseAmount(newGoal) !== goal) {
        body.goal = parseAmount(newGoal)!
      }
      return piggyApi.update(piggyId, body)
    },
    onSuccess: () => onDone(),
  })

  function submit() {
    const next: Record<string, string> = {}
    const n = nameError(newName)
    if (n) next.name = n
    const g = goalError(newGoal)
    if (g) next.goal = g
    setErrors(next)
    if (Object.keys(next).length === 0) save.mutate()
  }

  return (
    <form className="flex flex-col gap-4" onSubmit={(e) => { e.preventDefault(); submit() }} noValidate>
      {save.isError && <Alert tone="error">{messageFor(save.error)}</Alert>}
      <Field label="Nome" htmlFor="edit-name" error={errors.name}>
        <Input id="edit-name" value={newName} maxLength={NAME_MAX} aria-invalid={!!errors.name} onChange={(e) => setNewName(e.target.value)} />
      </Field>
      <Field label="Meta (R$)" htmlFor="edit-goal" error={errors.goal} hint="Em branco remove a meta">
        <Input id="edit-goal" inputMode="decimal" className="num" placeholder="0,00" value={newGoal} aria-invalid={!!errors.goal} onChange={(e) => setNewGoal(sanitizeAmount(e.target.value))} />
      </Field>
      <div><Button type="submit" variant="secondary" loading={save.isPending}>Salvar alterações</Button></div>
    </form>
  )
}
