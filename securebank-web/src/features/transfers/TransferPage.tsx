import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useForm, useWatch } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { CheckCircle } from '@phosphor-icons/react'
import { Link } from 'react-router'
import { bankingApi } from '../../services/banking'
import { createIdempotency } from '../../lib/idempotency'
import { formatBRL, parseAmount } from '../../lib/money'
import { accountLabel, accountTypeLabel } from '../../lib/format'
import { ApiError } from '../../lib/api'
import { messageFor } from '../../lib/errors'
import { accountNumberValid, amountIssue, balanceIssue, limitIssue, maskAccountNumber, sameAccount, sanitizeAmount, withMask } from '../../lib/validation'
import { Alert, Button, EmptyState, Field, Input, PageHeader, Panel, Select, Skeleton } from '../../components/ui'
import { useAccounts, useRefreshMoney } from '../accounts/hooks'

const schema = z.object({
  sourceAccountId: z.string().min(1, 'Escolha a conta de origem'),
  destinationBranch: z.string().regex(/^\d{4}$/, 'A agência tem 4 dígitos'),
  destinationAccountNumber: z
    .string()
    .regex(/^\d{6,12}-\d$/, 'Use o formato 123456-0')
    .refine(accountNumberValid, 'Número de conta inválido: confira o dígito depois do hífen'),
  amount: z.string().superRefine((v, ctx) => {
    const m = amountIssue(v)
    if (m) ctx.addIssue({ code: 'custom', message: m })
  }),
  description: z.string().max(140, 'No máximo 140 caracteres').optional(),
})
type Values = z.infer<typeof schema>

export function TransferPage() {
  const accounts = useAccounts()
  const refresh = useRefreshMoney()
  const [idem] = useState(() => createIdempotency())
  const [review, setReview] = useState<Values | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [sent, setSent] = useState<{ amount: string; to: string } | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: { sourceAccountId: '', destinationBranch: '0001', destinationAccountNumber: '', amount: '', description: '' },
  })
  const errors = form.formState.errors
  const sourceId = useWatch({ control: form.control, name: 'sourceAccountId' })
  const limits = useQuery({ queryKey: ['limits', sourceId], queryFn: () => bankingApi.limits(sourceId), enabled: !!sourceId })
  const transferLimit = limits.data?.find((l) => l.type === 'TRANSFER')

  /** Confere o que depende da conta de origem (mesma conta, saldo, limites) antes de abrir a revisão. */
  function startReview(v: Values) {
    const source = accounts.data?.find((a) => a.id === v.sourceAccountId)
    const amount = parseAmount(v.amount)!
    let ok = true
    if (source && sameAccount(source, v.destinationBranch, v.destinationAccountNumber)) {
      form.setError('destinationAccountNumber', { message: 'Escolha uma conta diferente da de origem' })
      ok = false
    }
    const issue = (source ? balanceIssue(amount, source.balance.amount) : null) ?? limitIssue(amount, transferLimit)
    if (issue) {
      form.setError('amount', { message: issue })
      ok = false
    }
    if (ok) setReview(v)
  }

  async function confirm() {
    if (!review) return
    setError(null)
    setSubmitting(true)
    const body = {
      sourceAccountId: review.sourceAccountId,
      destinationBranch: review.destinationBranch,
      destinationAccountNumber: review.destinationAccountNumber,
      amount: parseAmount(review.amount)!,
      description: review.description?.trim() || undefined,
    }
    try {
      await bankingApi.transfer(body, idem.keyFor(body))
      idem.settle()
      setSent({ amount: body.amount, to: `${body.destinationBranch} / ${body.destinationAccountNumber}` })
      setReview(null)
      form.reset()
      await refresh()
    } catch (e) {
      if (!(e instanceof ApiError) || !e.uncertain) idem.settle()
      setError(messageFor(e))
    } finally {
      setSubmitting(false)
    }
  }

  if (accounts.isPending) return <Skeleton className="h-64" />
  if (!accounts.data?.length) {
    return (
      <>
        <PageHeader title="Transferir" />
        <EmptyState title="Você precisa de uma conta para transferir" action={<Link to="/contas"><Button>Abrir conta</Button></Link>} />
      </>
    )
  }

  if (sent) {
    return (
      <div className="mx-auto max-w-md pt-8 text-center">
        <CheckCircle size={48} weight="duotone" className="mx-auto text-ok" />
        <h1 className="mt-4 text-2xl font-semibold">Transferência enviada</h1>
        <p className="num mt-2 text-3xl font-semibold">{formatBRL(sent.amount)}</p>
        <p className="mt-1 text-sm text-muted">para a conta {sent.to}</p>
        <div className="mt-8 flex justify-center gap-3">
          <Button onClick={() => setSent(null)}>Nova transferência</Button>
          <Link to="/"><Button variant="secondary">Voltar ao início</Button></Link>
        </div>
      </div>
    )
  }

  const source = accounts.data.find((a) => a.id === review?.sourceAccountId)

  return (
    <>
      <PageHeader title="Transferir" description="Envie dinheiro para outra conta do SecureBank pela agência e número da conta." />
      <div className="max-w-xl">
        {review ? (
          <Panel title="Confirme os dados">
            <dl className="flex flex-col gap-4 text-sm">
              <Row label="Valor" value={<span className="num text-lg font-semibold">{formatBRL(parseAmount(review.amount)!)}</span>} />
              <Row label="De" value={source ? `${accountTypeLabel(source.type)}, ${accountLabel(source)}` : ''} />
              <Row label="Para" value={`Ag. ${review.destinationBranch}, conta ${review.destinationAccountNumber}`} />
              {review.description && <Row label="Descrição" value={review.description} />}
            </dl>
            {error && <div className="mt-5"><Alert tone="error">{error}</Alert></div>}
            <div className="mt-6 flex gap-3">
              <Button loading={submitting} onClick={confirm}>Confirmar transferência</Button>
              <Button variant="secondary" disabled={submitting} onClick={() => { setReview(null); setError(null) }}>Editar</Button>
            </div>
          </Panel>
        ) : (
          <form onSubmit={form.handleSubmit(startReview)} noValidate className="flex flex-col gap-5">
            <Field label="Conta de origem" htmlFor="source" error={errors.sourceAccountId?.message}>
              <Select id="source" aria-invalid={!!errors.sourceAccountId} {...form.register('sourceAccountId')}>
                <option value="">Selecione</option>
                {accounts.data.map((a) => (
                  <option key={a.id} value={a.id}>
                    {accountTypeLabel(a.type)}, {a.accountNumber} ({formatBRL(a.balance.amount)})
                  </option>
                ))}
              </Select>
            </Field>
            <div className="grid gap-5 sm:grid-cols-[1fr_2fr]">
              <Field label="Agência" htmlFor="branch" error={errors.destinationBranch?.message}>
                <Input id="branch" inputMode="numeric" maxLength={4} className="num" aria-invalid={!!errors.destinationBranch} {...form.register('destinationBranch')} />
              </Field>
              <Field label="Conta de destino" htmlFor="number" error={errors.destinationAccountNumber?.message}>
                <Input id="number" placeholder="123456-0" maxLength={14} className="num" aria-invalid={!!errors.destinationAccountNumber} {...withMask(form.register('destinationAccountNumber'), maskAccountNumber)} />
              </Field>
            </div>
            <Field
              label="Valor (R$)"
              htmlFor="amount"
              error={errors.amount?.message}
              hint={transferLimit ? `Limite por operação ${formatBRL(transferLimit.perOperation.amount)} · restante hoje ${formatBRL(transferLimit.remainingToday.amount)}` : undefined}
            >
              <Input id="amount" inputMode="decimal" placeholder="0,00" autoComplete="off" className="num" aria-invalid={!!errors.amount} {...withMask(form.register('amount'), sanitizeAmount)} />
            </Field>
            <Field label="Descrição (opcional)" htmlFor="description" error={errors.description?.message}>
              <Input id="description" maxLength={140} {...form.register('description')} />
            </Field>
            <Button type="submit">Revisar</Button>
          </form>
        )}
      </div>
    </>
  )
}

function Row({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className="flex items-baseline justify-between gap-6 border-b border-line pb-4 last:border-b-0 last:pb-0">
      <dt className="text-muted">{label}</dt>
      <dd className="text-right">{value}</dd>
    </div>
  )
}
