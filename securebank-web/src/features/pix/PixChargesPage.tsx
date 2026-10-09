import { useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { Link } from 'react-router'
import { bankingApi } from '../../services/banking'
import { pixApi, type PixCharge } from '../../services/pix'
import { encodeDynamic } from '../../lib/brcode'
import { formatDateTime } from '../../lib/format'
import { messageFor } from '../../lib/errors'
import { formatBRL, parseAmount } from '../../lib/money'
import { chargeStatusLabel, chargeValidities } from '../../lib/pix'
import { amountIssue, sanitizeAmount } from '../../lib/validation'
import { Alert, Badge, Button, EmptyState, ErrorState, Field, Input, PageHeader, Panel, Select, Skeleton } from '../../components/ui'
import { ConfirmButton } from '../../components/ConfirmButton'
import { useAccounts } from '../accounts/hooks'
import { useRefreshPix } from './hooks'
import { AccountSelect, CopyButton, QrImage } from './parts'

const tones = { ACTIVE: 'warn', PAID: 'ok', CANCELED: 'neutral', EXPIRED: 'neutral' } as const

/** Cobrar: cria uma cobrança de valor fixo, validade e uso único; o QR leva o endereço dela, não a chave. */
export function PixChargesPage() {
  const accounts = useAccounts()
  const me = useQuery({ queryKey: ['me'], queryFn: bankingApi.me, staleTime: 5 * 60_000 })
  const charges = useQuery({ queryKey: ['pix', 'charges'], queryFn: () => pixApi.charges() })
  const refresh = useRefreshPix()
  const [accountId, setAccountId] = useState('')
  const [amount, setAmount] = useState('')
  const [description, setDescription] = useState('')
  const [minutes, setMinutes] = useState(1440)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [shown, setShown] = useState<PixCharge | null>(null)

  const list = accounts.data ?? []
  const effectiveAccount = accountId || (list.length === 1 ? list[0].id : '')
  const create = useMutation({
    mutationFn: () => pixApi.createCharge({ accountId: effectiveAccount, amount: parseAmount(amount)!, description: description.trim() || undefined, expiresInMinutes: minutes }),
    onSuccess: async (charge) => {
      setShown(charge)
      setAmount('')
      setDescription('')
      await refresh()
    },
  })
  const cancel = useMutation({ mutationFn: (c: PixCharge) => pixApi.cancelCharge(c.txid), onSuccess: async (_, c) => { if (shown?.txid === c.txid) setShown(null); await refresh() } })

  function submit() {
    const next: Record<string, string> = {}
    if (!effectiveAccount) next.account = 'Escolha a conta que vai receber'
    const amountProblem = amountIssue(amount)
    if (amountProblem) next.amount = amountProblem
    if (description.length > 140) next.description = 'No máximo 140 caracteres'
    setErrors(next)
    if (Object.keys(next).length === 0) create.mutate()
  }

  if (accounts.isPending) return <Skeleton className="h-64" />
  if (!list.length) return <EmptyState title="Você precisa de uma conta para cobrar" action={<Link to="/contas"><Button>Abrir conta</Button></Link>} />

  const code = shown ? encodeDynamic(shown.location, me.data?.name ?? '') : null

  return (
    <>
      <PageHeader title="Cobrar" description="Crie uma cobrança de valor fixo. O QR vale uma vez só e até a validade escolhida." />
      <div className="grid gap-6 lg:grid-cols-[1fr_20rem]">
        <div className="flex max-w-xl flex-col gap-5">
          {create.isError && <Alert tone="error">{messageFor(create.error)}</Alert>}
          {list.length > 1 && <AccountSelect id="account" label="Receber na conta" accounts={list} value={effectiveAccount} onChange={setAccountId} error={errors.account} />}
          <Field label="Valor (R$)" htmlFor="amount" error={errors.amount}>
            <Input id="amount" inputMode="decimal" className="num" placeholder="0,00" autoComplete="off" value={amount} aria-invalid={!!errors.amount} onChange={(e) => setAmount(sanitizeAmount(e.target.value))} />
          </Field>
          <Field label="Descrição (opcional)" htmlFor="description" error={errors.description}>
            <Input id="description" maxLength={140} value={description} onChange={(e) => setDescription(e.target.value)} />
          </Field>
          <Field label="Validade" htmlFor="validity">
            <Select id="validity" value={minutes} onChange={(e) => setMinutes(Number(e.target.value))}>
              {chargeValidities.map((v) => <option key={v.minutes} value={v.minutes}>{v.label}</option>)}
            </Select>
          </Field>
          <div><Button loading={create.isPending} onClick={submit}>Criar cobrança</Button></div>
        </div>
        {shown && code && (
          <Panel title={formatBRL(shown.amount.amount)}>
            <QrImage value={code} />
            <p className="mt-3 text-center text-xs text-muted">Vale até {formatDateTime(shown.expiresAt)}</p>
            <p className="card mt-4 break-all p-3 text-xs text-muted">{code}</p>
            <div className="mt-4 flex flex-wrap justify-center gap-2">
              <CopyButton text={code} />
              <Button variant="ghost" onClick={() => setShown(null)}>Fechar</Button>
            </div>
          </Panel>
        )}
      </div>

      <h2 className="mb-3 mt-10 text-sm font-medium">Suas cobranças</h2>
      {charges.isPending && <Skeleton className="h-32" />}
      {charges.isError && <ErrorState message={messageFor(charges.error)} onRetry={() => charges.refetch()} />}
      {charges.data?.items.length === 0 && <p className="text-sm text-muted">Nenhuma cobrança criada ainda.</p>}
      {cancel.isError && <div className="mb-3"><Alert tone="error">{messageFor(cancel.error)}</Alert></div>}
      {!!charges.data?.items.length && (
        <ul className="card divide-y divide-line overflow-hidden">
          {charges.data.items.map((c) => (
            <li key={c.txid} className="flex flex-wrap items-center justify-between gap-3 px-5 py-4">
              <div className="min-w-0">
                <p className="flex flex-wrap items-center gap-2 text-sm font-medium">
                  {c.description ?? 'Cobrança'} <Badge tone={tones[c.status]}>{chargeStatusLabel(c.status)}</Badge>
                </p>
                <p className="text-xs text-muted">
                  {c.status === 'PAID' && c.paidAt ? `Paga em ${formatDateTime(c.paidAt)}` : `Vence em ${formatDateTime(c.expiresAt)}`}
                </p>
              </div>
              <div className="flex items-center gap-3">
                <span className="num font-medium">{formatBRL(c.amount.amount)}</span>
                {c.status === 'ACTIVE' && (
                  <>
                    <Button variant="secondary" onClick={() => setShown(c)}>Mostrar QR</Button>
                    <ConfirmButton label="Cancelar" confirmLabel="Confirmar cancelamento" loading={cancel.isPending} onConfirm={() => cancel.mutate(c)} />
                  </>
                )}
              </div>
            </li>
          ))}
        </ul>
      )}
    </>
  )
}
