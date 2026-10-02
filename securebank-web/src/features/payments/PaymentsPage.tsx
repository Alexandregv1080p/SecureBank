import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { Link } from 'react-router'
import { bankingApi } from '../../services/banking'
import { createIdempotency } from '../../lib/idempotency'
import { formatBRL, parseAmount } from '../../lib/money'
import { accountTypeLabel, formatDateTime } from '../../lib/format'
import { ApiError } from '../../lib/api'
import { messageFor } from '../../lib/errors'
import { Alert, Badge, Button, EmptyState, ErrorState, Field, Input, PageHeader, Panel, Select, Skeleton } from '../../components/ui'
import { useAccounts, useRefreshMoney } from '../accounts/hooks'

const digits = (v: string) => v.replace(/\D/g, '')

const schema = z.object({
  accountId: z.string().min(1, 'Escolha a conta'),
  barcode: z.string().refine((v) => [44, 47, 48].includes(digits(v).length), 'O código tem 44, 47 ou 48 dígitos'),
  amount: z.string().refine((v) => parseAmount(v) !== null, 'Informe um valor maior que zero, com até 2 casas decimais'),
  description: z.string().max(140, 'No máximo 140 caracteres').optional(),
})
type Values = z.infer<typeof schema>

export function PaymentsPage() {
  const accounts = useAccounts()
  const recent = useQuery({ queryKey: ['payments'], queryFn: () => bankingApi.payments(0) })
  const refresh = useRefreshMoney()
  const [idem] = useState(() => createIdempotency())
  const [error, setError] = useState<string | null>(null)
  const [ok, setOk] = useState<string | null>(null)
  const form = useForm<Values>({ resolver: zodResolver(schema), defaultValues: { accountId: '', barcode: '', amount: '', description: '' } })
  const errors = form.formState.errors

  async function onSubmit(v: Values) {
    setError(null)
    setOk(null)
    const body = { accountId: v.accountId, amount: parseAmount(v.amount)!, barcode: digits(v.barcode), description: v.description?.trim() || undefined }
    try {
      await bankingApi.pay(body, idem.keyFor(body))
      idem.settle()
      form.reset({ ...form.getValues(), barcode: '', amount: '', description: '' })
      setOk(`Pagamento de ${formatBRL(body.amount)} realizado.`)
      await Promise.all([refresh(), recent.refetch()])
    } catch (e) {
      if (!(e instanceof ApiError) || !e.uncertain) idem.settle()
      setError(messageFor(e))
    }
  }

  return (
    <>
      <PageHeader title="Pagar" description="Pague boletos e convênios com o saldo da sua conta." />
      {accounts.isPending && <Skeleton className="h-64" />}
      {accounts.data && !accounts.data.length && (
        <EmptyState title="Você precisa de uma conta para pagar" action={<Link to="/contas"><Button>Abrir conta</Button></Link>} />
      )}
      {!!accounts.data?.length && (
        <div className="grid gap-10 lg:grid-cols-[1fr_1fr]">
          <form onSubmit={form.handleSubmit(onSubmit)} noValidate className="flex max-w-xl flex-col gap-5">
            {error && <Alert tone="error">{error}</Alert>}
            {ok && <Alert tone="success">{ok}</Alert>}
            <Field label="Pagar com" htmlFor="account" error={errors.accountId?.message}>
              <Select id="account" aria-invalid={!!errors.accountId} {...form.register('accountId')}>
                <option value="">Selecione</option>
                {accounts.data.map((a) => (
                  <option key={a.id} value={a.id}>
                    {accountTypeLabel(a.type)}, {a.accountNumber} ({formatBRL(a.balance.amount)})
                  </option>
                ))}
              </Select>
            </Field>
            <Field label="Código de barras ou linha digitável" htmlFor="barcode" error={errors.barcode?.message}>
              <Input id="barcode" inputMode="numeric" autoComplete="off" className="num" aria-invalid={!!errors.barcode} {...form.register('barcode')} />
            </Field>
            <Field label="Valor (R$)" htmlFor="pay-amount" error={errors.amount?.message}>
              <Input id="pay-amount" inputMode="decimal" placeholder="0,00" autoComplete="off" className="num" aria-invalid={!!errors.amount} {...form.register('amount')} />
            </Field>
            <Field label="Descrição (opcional)" htmlFor="pay-description" error={errors.description?.message}>
              <Input id="pay-description" maxLength={140} {...form.register('description')} />
            </Field>
            <Button type="submit" loading={form.formState.isSubmitting}>Pagar</Button>
          </form>

          <Panel title="Pagamentos recentes">
            {recent.isPending && <Skeleton className="h-32" />}
            {recent.isError && <ErrorState message={messageFor(recent.error)} onRetry={() => recent.refetch()} />}
            {recent.data?.items.length === 0 && <p className="text-sm text-muted">Nenhum pagamento ainda.</p>}
            {!!recent.data?.items.length && (
              <ul className="flex flex-col divide-y divide-line">
                {recent.data.items.map((p) => (
                  <li key={p.id} className="flex items-center justify-between gap-4 py-3 first:pt-0 last:pb-0">
                    <div>
                      <p className="text-sm font-medium">{p.description ?? 'Pagamento de boleto'}</p>
                      <p className="text-xs text-muted">{formatDateTime(p.createdAt)}</p>
                    </div>
                    <div className="text-right">
                      <p className="num text-sm">{formatBRL(p.amount.amount)}</p>
                      <Badge tone={p.status === 'COMPLETED' ? 'ok' : 'neutral'}>{p.status === 'COMPLETED' ? 'Concluído' : p.status}</Badge>
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </Panel>
        </div>
      )}
    </>
  )
}
