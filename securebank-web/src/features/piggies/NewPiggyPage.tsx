import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { Link, useNavigate } from 'react-router'
import { piggyApi } from '../../services/piggies'
import { messageFor } from '../../lib/errors'
import { goalError, nameError, NAME_MAX } from '../../lib/piggy'
import { parseAmount } from '../../lib/money'
import { sanitizeAmount } from '../../lib/validation'
import { Alert, Button, EmptyState, Field, Input, PageHeader, Skeleton } from '../../components/ui'
import { useAccounts } from '../accounts/hooks'
import { AccountSelect } from '../pix/parts'
import { useRefreshPiggies } from './hooks'

export function NewPiggyPage() {
  const accounts = useAccounts()
  const navigate = useNavigate()
  const refresh = useRefreshPiggies()
  const [accountId, setAccountId] = useState('')
  const [name, setName] = useState('')
  const [goal, setGoal] = useState('')
  const [errors, setErrors] = useState<Record<string, string>>({})
  const list = accounts.data ?? []
  const effectiveAccount = accountId || (list.length === 1 ? list[0].id : '')

  const create = useMutation({
    mutationFn: () => piggyApi.create({ accountId: effectiveAccount, name: name.trim(), goal: goal.trim() ? parseAmount(goal)! : undefined }),
    onSuccess: async (piggy) => {
      await refresh()
      navigate(`/porquinhos/${piggy.id}`, { replace: true })
    },
  })

  function submit() {
    const next: Record<string, string> = {}
    if (!effectiveAccount) next.account = 'Escolha a conta de onde vai o dinheiro'
    const n = nameError(name)
    if (n) next.name = n
    const g = goalError(goal)
    if (g) next.goal = g
    setErrors(next)
    if (Object.keys(next).length === 0) create.mutate()
  }

  if (accounts.isPending) return <Skeleton className="h-48" />
  if (!list.length) return <EmptyState title="Você precisa de uma conta" action={<Link to="/contas"><Button>Abrir conta</Button></Link>}>Abra uma conta antes de criar um porquinho.</EmptyState>

  return (
    <>
      <PageHeader title="Novo porquinho" description="Dê um nome e, se quiser, uma meta. Você guarda e resgata quando quiser." />
      <form className="flex max-w-xl flex-col gap-5" onSubmit={(e) => { e.preventDefault(); submit() }} noValidate>
        {create.isError && <Alert tone="error">{messageFor(create.error)}</Alert>}
        <Field label="Nome" htmlFor="name" error={errors.name} hint="Ex.: Viagem, Reserva de emergência">
          <Input id="name" value={name} maxLength={NAME_MAX} autoComplete="off" aria-invalid={!!errors.name} onChange={(e) => setName(e.target.value)} />
        </Field>
        <Field label="Meta (opcional)" htmlFor="goal" error={errors.goal} hint="Deixe em branco para guardar sem meta">
          <Input id="goal" inputMode="decimal" className="num" placeholder="0,00" autoComplete="off" value={goal} aria-invalid={!!errors.goal} onChange={(e) => setGoal(sanitizeAmount(e.target.value))} />
        </Field>
        {list.length > 1 && <AccountSelect id="account" label="Guardar a partir da conta" accounts={list} value={effectiveAccount} onChange={setAccountId} error={errors.account} />}
        <div className="flex gap-3">
          <Button type="submit" loading={create.isPending}>Criar porquinho</Button>
          <Link to="/porquinhos"><Button type="button" variant="secondary">Cancelar</Button></Link>
        </div>
      </form>
    </>
  )
}
