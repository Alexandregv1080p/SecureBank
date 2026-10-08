import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router'
import { staffApi, type LimitAdmin } from '../../services/staff'
import { accountLabel, accountTypeLabel } from '../../lib/format'
import { formatBRL, parseAmount } from '../../lib/money'
import { messageFor } from '../../lib/errors'
import { isUuid, limitLabels } from '../../lib/staff'
import { Alert, Badge, Button, ErrorState, Field, Input, PageHeader, Panel, Skeleton } from '../../components/ui'
import { ConfirmButton } from '../../components/ConfirmButton'
import { DataList, DataRow } from './parts'
import { useIdParam } from './useIdParam'

export function AccountsAdminPage() {
  const [id, setId] = useIdParam()
  const [input, setInput] = useState(id)
  const valid = isUuid(id)
  const client = useQueryClient()
  const account = useQuery({ queryKey: ['staff', 'account', id], queryFn: () => staffApi.account(id.trim()), enabled: valid })
  const limits = useQuery({ queryKey: ['staff', 'limits', id], queryFn: () => staffApi.limits(id.trim()), enabled: valid && account.isSuccess })
  const refresh = () => Promise.all([client.invalidateQueries({ queryKey: ['staff', 'account', id] }), client.invalidateQueries({ queryKey: ['staff', 'limits', id] })])
  const toggle = useMutation({
    mutationFn: () => (account.data?.status === 'BLOCKED' ? staffApi.unblock(id.trim()) : staffApi.block(id.trim())),
    onSuccess: refresh,
  })

  const blocked = account.data?.status === 'BLOCKED'
  return (
    <>
      <PageHeader title="Contas e limites" description="Bloqueie ou desbloqueie uma conta e ajuste os limites. O cliente é avisado do bloqueio." />
      <form className="mb-6 flex max-w-xl items-end gap-3" onSubmit={(e) => { e.preventDefault(); setId(input.trim()) }}>
        <div className="flex-1">
          <Field label="Identificador da conta" htmlFor="account" error={id && !valid ? 'Informe um identificador completo (UUID).' : undefined}>
            <Input id="account" value={input} onChange={(e) => setInput(e.target.value)} placeholder="00000000-0000-0000-0000-000000000000" />
          </Field>
        </div>
        <Button type="submit">Buscar</Button>
      </form>

      {valid && account.isPending && <Skeleton className="h-40" />}
      {account.isError && <ErrorState message={messageFor(account.error)} onRetry={() => account.refetch()} />}
      {account.data && (
        <div className="flex flex-col gap-6">
          <Panel
            title={`${accountTypeLabel(account.data.type)} · ${accountLabel(account.data)}`}
            action={<Badge tone={blocked ? 'danger' : account.data.status === 'ACTIVE' ? 'ok' : 'warn'}>{blocked ? 'Bloqueada' : account.data.status === 'ACTIVE' ? 'Ativa' : 'Encerrada'}</Badge>}
          >
            <div className="flex flex-col gap-4">
              <DataList>
                <DataRow label="Conta"><span className="font-mono text-xs">{account.data.id}</span></DataRow>
                <DataRow label="Cliente">
                  <Link className="font-mono text-xs text-accent hover:underline" to={`/equipe/clientes?id=${account.data.customerId}`}>{account.data.customerId}</Link>
                </DataRow>
              </DataList>
              {toggle.isError && <Alert tone="error">{messageFor(toggle.error)}</Alert>}
              {account.data.status !== 'CLOSED' && (
                <div>
                  <ConfirmButton
                    label={blocked ? 'Desbloquear conta' : 'Bloquear conta'}
                    confirmLabel={blocked ? 'Confirmar desbloqueio' : 'Confirmar bloqueio'}
                    variant={blocked ? 'secondary' : 'danger'}
                    loading={toggle.isPending}
                    onConfirm={() => toggle.mutate()}
                  />
                </div>
              )}
            </div>
          </Panel>

          <Panel title="Limites">
            {limits.isPending && <Skeleton className="h-32" />}
            {limits.isError && <ErrorState message={messageFor(limits.error)} onRetry={() => limits.refetch()} />}
            {limits.data && (
              <ul className="divide-y divide-line">
                {limits.data.map((l) => (
                  <LimitRow key={`${l.type}-${l.perOperation.amount}-${l.daily.amount}`} accountId={id.trim()} limit={l} onSaved={refresh} />
                ))}
              </ul>
            )}
          </Panel>
        </div>
      )}
    </>
  )
}

function LimitRow({ accountId, limit, onSaved }: { accountId: string; limit: LimitAdmin; onSaved: () => unknown }) {
  const [perOp, setPerOp] = useState(limit.perOperation.amount.replace('.', ','))
  const [daily, setDaily] = useState(limit.daily.amount.replace('.', ','))
  const perOpValue = parseAmount(perOp)
  const dailyValue = parseAmount(daily)
  const error = !perOpValue || !dailyValue ? 'Informe valores positivos com até 2 casas.' : Number(dailyValue) < Number(perOpValue) ? 'O limite diário não pode ser menor que o por operação.' : undefined
  const save = useMutation({ mutationFn: () => staffApi.changeLimit(accountId, limit.type, perOpValue!, dailyValue!), onSuccess: onSaved })
  const changed = perOpValue !== limit.perOperation.amount || dailyValue !== limit.daily.amount

  return (
    <li className="flex flex-col gap-3 py-4 first:pt-0 last:pb-0">
      <div className="flex items-center justify-between">
        <p className="font-medium">{limitLabels[limit.type] ?? limit.type}</p>
        <p className="text-xs text-muted">Usado hoje: {formatBRL(limit.usedToday.amount)}</p>
      </div>
      <div className="grid gap-3 sm:grid-cols-[1fr_1fr_auto] sm:items-end">
        <Field label="Por operação (R$)" htmlFor={`${limit.type}-op`}>
          <Input id={`${limit.type}-op`} inputMode="decimal" value={perOp} onChange={(e) => setPerOp(e.target.value)} />
        </Field>
        <Field label="Por dia (R$)" htmlFor={`${limit.type}-day`}>
          <Input id={`${limit.type}-day`} inputMode="decimal" value={daily} onChange={(e) => setDaily(e.target.value)} />
        </Field>
        <Button loading={save.isPending} disabled={!!error || !changed} onClick={() => save.mutate()}>Salvar</Button>
      </div>
      {error && changed && <p role="alert" className="text-xs font-medium text-danger">{error}</p>}
      {save.isError && <Alert tone="error">{messageFor(save.error)}</Alert>}
      {save.isSuccess && !changed && <Alert tone="success">Limite atualizado.</Alert>}
    </li>
  )
}
