import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { bankingApi } from '../../services/banking'
import { createIdempotency } from '../../lib/idempotency'
import { formatBRL, parseAmount } from '../../lib/money'
import { amountIssue, balanceIssue, limitIssue, sanitizeAmount, withMask } from '../../lib/validation'
import type { LimitUsage } from '../../services/types'
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
export function MoneyForm({ accountId, kind, balance, limit }: { accountId: string; kind: 'deposit' | 'withdraw'; balance?: string; limit?: LimitUsage }) {
  const refresh = useRefreshMoney()
  const [idem] = useState(() => createIdempotency())
  const [error, setError] = useState<string | null>(null)
  const [done, setDone] = useState<string | null>(null)
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
      await (kind === 'deposit' ? bankingApi.deposit : bankingApi.withdraw)(accountId, value, key)
      idem.settle()
      form.reset()
      setDone(kind === 'deposit' ? 'Depósito realizado.' : 'Saque realizado.')
      await refresh()
    } catch (e) {
      if (!(e instanceof ApiError) || !e.uncertain) idem.settle()
      setError(messageFor(e))
    }
  }

  return (
    <form onSubmit={form.handleSubmit(onSubmit)} noValidate className="flex flex-col gap-4">
      {error && <Alert tone="error">{error}</Alert>}
      {done && <Alert tone="success">{done}</Alert>}
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
