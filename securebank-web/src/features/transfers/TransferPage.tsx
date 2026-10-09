import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useForm, useWatch } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { Link } from 'react-router'
import { bankingApi } from '../../services/banking'
import type { Destination, Transfer } from '../../services/types'
import { createIdempotency } from '../../lib/idempotency'
import { formatBRL, parseAmount, subtractAmounts } from '../../lib/money'
import { titleCase } from '../../lib/dashboard'
import { accountLabel, accountTypeLabel, formatDateTime } from '../../lib/format'
import { ApiError } from '../../lib/api'
import { messageFor } from '../../lib/errors'
import { accountNumberValid, amountIssue, balanceIssue, limitIssue, maskAccountNumber, sameAccount, sanitizeAmount, withMask } from '../../lib/validation'
import { Alert, Button, EmptyState, Field, Input, PageHeader, Panel, Select, Skeleton } from '../../components/ui'
import { useAccounts, useRefreshMoney } from '../accounts/hooks'
import { Receipt } from '../pix/parts'

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

/** Quem vai receber, já confirmado pelo servidor (nome e CPF mascarados), junto com o que foi digitado. */
type Review = { values: Values; destination: Destination }
type Sent = { transfer: Transfer; destination: Destination; from: string }

/** Transferir: formulário → o servidor confirma quem recebe → revisão → comprovante imprimível. */
export function TransferPage() {
  const accounts = useAccounts()
  const refresh = useRefreshMoney()
  const [idem] = useState(() => createIdempotency())
  const [review, setReview] = useState<Review | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [sent, setSent] = useState<Sent | null>(null)
  const [checking, setChecking] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: { sourceAccountId: '', destinationBranch: '0001', destinationAccountNumber: '', amount: '', description: '' },
  })
  const errors = form.formState.errors
  const sourceId = useWatch({ control: form.control, name: 'sourceAccountId' })
  const limits = useQuery({ queryKey: ['limits', sourceId], queryFn: () => bankingApi.limits(sourceId), enabled: !!sourceId })
  const transferLimit = limits.data?.find((l) => l.type === 'TRANSFER')

  /** Confere o que depende da conta de origem (mesma conta, saldo, limites) e pergunta ao servidor quem é o destinatário. */
  async function startReview(v: Values) {
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
    if (!ok) return
    setChecking(true)
    setError(null)
    try {
      const found = await bankingApi.destination(v.destinationBranch, v.destinationAccountNumber)
      const destination = { ...found, name: titleCase(found.name) } // o cadastro guarda o nome como foi digitado
      if (destination.status !== 'ACTIVE') {
        form.setError('destinationAccountNumber', { message: 'Esta conta não está disponível para receber.' })
        return
      }
      setReview({ values: v, destination })
    } catch (e) {
      if (e instanceof ApiError && e.status === 404) form.setError('destinationAccountNumber', { message: 'Conta não encontrada. Confira a agência e o número.' })
      else setError(messageFor(e))
    } finally {
      setChecking(false)
    }
  }

  async function confirm() {
    if (!review) return
    const { values, destination } = review
    setError(null)
    setSubmitting(true)
    const body = {
      sourceAccountId: values.sourceAccountId,
      destinationBranch: values.destinationBranch,
      destinationAccountNumber: values.destinationAccountNumber,
      amount: parseAmount(values.amount)!,
      description: values.description?.trim() || undefined,
    }
    const from = accounts.data?.find((a) => a.id === values.sourceAccountId)
    try {
      const transfer = await bankingApi.transfer(body, idem.keyFor(body))
      idem.settle()
      setSent({ transfer, destination, from: from ? `${accountTypeLabel(from.type)}, ${accountLabel(from)}` : '' })
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
    const { transfer, destination, from } = sent
    return (
      <Receipt
        title="Transferência enviada"
        amount={transfer.amount.amount}
        lines={[
          { label: 'De', value: from },
          { label: 'Para', value: destination.name },
          { label: 'CPF', value: destination.document },
          { label: 'Banco', value: `${destination.bank}, ag. ${destination.branch}, conta ${destination.accountNumber}` },
          ...(transfer.description ? [{ label: 'Descrição', value: transfer.description }] : []),
          { label: 'Data', value: formatDateTime(transfer.createdAt) },
          { label: 'Identificador', value: <span className="num text-xs">{transfer.id}</span> },
        ]}
      >
        <Button onClick={() => setSent(null)}>Nova transferência</Button>
        <Link to="/"><Button variant="secondary">Voltar ao início</Button></Link>
      </Receipt>
    )
  }

  const source = accounts.data.find((a) => a.id === review?.values.sourceAccountId)

  return (
    <>
      <PageHeader title="Transferir" description="Envie dinheiro para outra conta do SecureBank pela agência e número da conta." />
      <div className="max-w-xl">
        {review ? (
          <Panel title="Confirme os dados">
            <dl className="flex flex-col gap-4 text-sm">
              <Row label="Valor" value={<span className="num text-lg font-semibold">{formatBRL(parseAmount(review.values.amount)!)}</span>} />
              <Row label="De" value={source ? `${accountTypeLabel(source.type)}, ${accountLabel(source)}` : ''} />
              <Row
                label="Para"
                value={
                  <>
                    <span className="block font-medium">{review.destination.name}</span>
                    <span className="block text-muted">CPF {review.destination.document}</span>
                    <span className="num block text-muted">
                      {review.destination.bank}, ag. {review.destination.branch}, conta {review.destination.accountNumber}
                    </span>
                  </>
                }
              />
              {review.values.description && <Row label="Descrição" value={review.values.description} />}
              {source && <Row label="Saldo depois" value={<span className="num">{formatBRL(subtractAmounts(source.balance.amount, parseAmount(review.values.amount)!))}</span>} />}
            </dl>
            <p className="mt-5 text-xs text-muted">Confira o nome: depois de enviada, a transferência não pode ser desfeita por aqui.</p>
            {error && <div className="mt-5"><Alert tone="error">{error}</Alert></div>}
            <div className="mt-6 flex gap-3">
              <Button loading={submitting} onClick={confirm}>Confirmar transferência</Button>
              <Button variant="secondary" disabled={submitting} onClick={() => { setReview(null); setError(null) }}>Editar</Button>
            </div>
          </Panel>
        ) : (
          <form onSubmit={form.handleSubmit(startReview)} noValidate className="flex flex-col gap-5">
            {error && <Alert tone="error">{error}</Alert>}
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
            <Button type="submit" loading={checking}>Revisar</Button>
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
