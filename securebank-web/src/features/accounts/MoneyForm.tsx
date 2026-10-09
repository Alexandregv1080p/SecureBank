import { useState } from 'react'
import { CheckCircle, Printer } from '@phosphor-icons/react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { bankingApi } from '../../services/banking'
import { createIdempotency } from '../../lib/idempotency'
import { formatBRL, parseAmount } from '../../lib/money'
import { amountIssue, balanceIssue, limitIssue, sanitizeAmount, withMask } from '../../lib/validation'
import type { LimitUsage, Transaction } from '../../services/types'
import { formatDateTime } from '../../lib/format'
import { ApiError } from '../../lib/api'
import { messageFor } from '../../lib/errors'
import { Alert, Button, Field, Input } from '../../components/ui'
import { useRefreshMoney } from './hooks'

const schema = z.object({
  amount: z.string().superRefine((v, ctx) => {
    const m = amountIssue(v)
    if (m) ctx.addIssue({ code: 'custom', message: m })
  }),
})

function withdrawHint(balance?: string, limit?: LimitUsage): string | undefined {
  const parts = [
    balance && `Saldo ${formatBRL(balance)}`,
    limit && `por operação até ${formatBRL(limit.perOperation.amount)}, restam ${formatBRL(limit.remainingToday.amount)} hoje`,
  ].filter(Boolean)
  return parts.length ? parts.join(' · ') : undefined
}

/** Depósito ou saque. A Idempotency-Key só é reaproveitada quando o resultado anterior foi incerto (rede, 5xx). */
export function MoneyForm({ accountId, accountName, kind, balance, limit }: { accountId: string; accountName?: string; kind: 'deposit' | 'withdraw'; balance?: string; limit?: LimitUsage }) {
  const refresh = useRefreshMoney()
  const [idem] = useState(() => createIdempotency())
  const [error, setError] = useState<string | null>(null)
  const [done, setDone] = useState<Transaction | null>(null)
  const form = useForm({ resolver: zodResolver(schema), defaultValues: { amount: '' } })
  const label = kind === 'deposit' ? 'Depositar' : 'Sacar'

  async function onSubmit({ amount }: z.infer<typeof schema>) {
    setError(null)
    setDone(null)
    const value = parseAmount(amount)!
    if (kind === 'withdraw') {
      const issue = (balance ? balanceIssue(value, balance) : null) ?? limitIssue(value, limit)
      if (issue) return form.setError('amount', { message: issue })
    }
    const key = idem.keyFor({ accountId, kind, value })
    try {
      const tx = await (kind === 'deposit' ? bankingApi.deposit : bankingApi.withdraw)(accountId, value, key)
      idem.settle()
      form.reset()
      setDone(tx)
      await refresh()
    } catch (e) {
      if (!(e instanceof ApiError) || !e.uncertain) idem.settle()
      setError(messageFor(e))
    }
  }

  if (done) {
    // comprovante: na tela é este bloco; ao imprimir (ou salvar em PDF) vira só ele, em preto sobre branco
    return (
      <div>
        <div className="print-area">
          <div className="flex items-center justify-between">
            <span className="font-semibold tracking-tight">SecureBank</span>
            <span className="text-xs text-muted">Comprovante</span>
          </div>
          <div className="mt-4 text-center">
            <CheckCircle size={36} weight="duotone" className="mx-auto text-ok" />
            <p className="mt-2 font-semibold">{kind === 'deposit' ? 'Depósito realizado' : 'Saque realizado'}</p>
            <p className="num mt-1 text-2xl font-semibold">{formatBRL(done.amount.amount)}</p>
          </div>
          <dl className="mt-4 flex flex-col gap-2 border-t border-line pt-4 text-sm">
            {accountName && <ReceiptRow label="Conta" value={accountName} />}
            <ReceiptRow label="Saldo depois" value={<span className="num">{formatBRL(done.balanceAfter.amount)}</span>} />
            <ReceiptRow label="Data" value={formatDateTime(done.createdAt)} />
            <ReceiptRow label="Identificador" value={<span className="num text-xs">{done.id}</span>} />
          </dl>
        </div>
        <div className="no-print mt-5 flex flex-wrap gap-3">
          <Button onClick={() => setDone(null)}>{kind === 'deposit' ? 'Novo depósito' : 'Novo saque'}</Button>
          <Button variant="secondary" onClick={() => window.print()}>
            <Printer size={18} /> Imprimir
          </Button>
        </div>
      </div>
    )
  }

  return (
    <form onSubmit={form.handleSubmit(onSubmit)} noValidate className="flex flex-col gap-4">
      {error && <Alert tone="error">{error}</Alert>}
      <Field
        label="Valor (R$)"
        htmlFor={`amount-${kind}`}
        error={form.formState.errors.amount?.message}
        hint={kind === 'withdraw' ? withdrawHint(balance, limit) : undefined}
      >
        <Input
          id={`amount-${kind}`}
          inputMode="decimal"
          placeholder="0,00"
          autoComplete="off"
          className="num"
          aria-invalid={!!form.formState.errors.amount}
          {...withMask(form.register('amount'), sanitizeAmount)}
        />
      </Field>
      <Button type="submit" loading={form.formState.isSubmitting}>
        {label}
      </Button>
    </form>
  )
}

function ReceiptRow({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className="flex justify-between gap-6">
      <dt className="shrink-0 text-muted">{label}</dt>
      <dd className="break-all text-right">{value}</dd>
    </div>
  )
}
