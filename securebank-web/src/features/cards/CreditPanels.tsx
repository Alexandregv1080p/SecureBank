import { useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { CaretDown, CaretUp, Receipt } from '@phosphor-icons/react'
import { useNavigate } from 'react-router'
import { creditApi } from '../../services/credit'
import type { Card, CreditSummary, Invoice, RefinancePlan } from '../../services/types'
import { ApiError } from '../../lib/api'
import { createIdempotency } from '../../lib/idempotency'
import { messageFor } from '../../lib/errors'
import { formatDateTime } from '../../lib/format'
import { formatBRL, parseAmount } from '../../lib/money'
import { MERCHANT_MAX } from '../../lib/card'
import { chargeLabel, creditPurchaseIssues, dueHint, holderIssue, installmentOptions, invoiceStatusLabel, limitIssue, MAX_ADDITIONAL, monthLabel, payIssue, shortDate } from '../../lib/credit'
import { sanitizeAmount } from '../../lib/validation'
import { Alert, Badge, Button, EmptyState, ErrorState, Field, Input, Panel, Select, Skeleton } from '../../components/ui'
import { Dialog } from '../../components/Dialog'

const tone = { OPEN: 'neutral', CLOSED: 'neutral', OVERDUE: 'danger', REVOLVING: 'warn', PAID: 'ok' } as const

/** Limite, quanto já está comprometido e a fatura do mês, no lugar do saldo do débito. */
export function CreditHero({ summary }: { summary: CreditSummary }) {
  const limit = Number(summary.creditLimit.amount)
  const usedPct = limit > 0 ? Math.min(100, Math.round((Number(summary.used.amount) / limit) * 100)) : 0
  const inv = summary.currentInvoice
  return (
    <div className="flex flex-col gap-4">
      <dl className="grid gap-x-6 gap-y-4 [grid-template-columns:repeat(auto-fit,minmax(12rem,1fr))]">
        <div>
          <dt className="text-xs uppercase tracking-wider text-muted">Limite disponível</dt>
          <dd className="num mt-1 text-2xl font-semibold tracking-tight">{formatBRL(summary.available.amount)}</dd>
          <dd className="mt-0.5 text-xs text-muted">de {formatBRL(summary.creditLimit.amount)}</dd>
        </div>
        <div>
          <dt className="text-xs uppercase tracking-wider text-muted">Fatura de {monthLabel(inv.month)}</dt>
          <dd className="num mt-1 text-2xl font-semibold tracking-tight">{formatBRL(inv.remaining.amount)}</dd>
          <dd className="mt-0.5 text-xs text-muted">{dueHint(inv)}</dd>
        </div>
      </dl>
      <div>
        <div className="h-2 overflow-hidden rounded-full bg-surface-2" role="progressbar" aria-valuenow={usedPct} aria-valuemin={0} aria-valuemax={100} aria-label="Limite usado">
          <div className={`h-full rounded-full transition-all ${summary.overdue ? 'bg-danger' : 'bg-accent'}`} style={{ width: `${usedPct}%` }} />
        </div>
        <p className="mt-1.5 text-xs text-muted">{formatBRL(summary.used.amount)} em uso · vencimento todo dia {summary.dueDay}</p>
      </div>
      {summary.overdue && <Alert tone="error">Há fatura vencida sem o pagamento mínimo. Pague ao menos o mínimo para voltar a comprar no crédito.</Alert>}
      {summary.mainCardId && <Alert tone="info">Cartão adicional: usa o limite e as faturas do cartão principal. Pagamentos e limite são feitos por lá.</Alert>}
    </div>
  )
}

/** Compra no crédito (simulada), à vista ou parcelada sem juros: usa limite agora e o dinheiro sai quando a fatura é paga. */
export function CreditPurchaseForm({ card, available, onDone }: { card: Card; available?: string; onDone: () => unknown }) {
  const [idem] = useState(() => createIdempotency())
  const [amount, setAmount] = useState('')
  const [merchant, setMerchant] = useState('')
  const [installments, setInstallments] = useState(1)
  const [errors, setErrors] = useState<{ amount?: string; merchant?: string }>({})
  const [error, setError] = useState<string | null>(null)
  const [done, setDone] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const options = installmentOptions(amount)
  const n = options.some((o) => o.n === installments) ? installments : 1

  async function submit() {
    const issues = creditPurchaseIssues(amount, merchant, n, available)
    setErrors(issues)
    if (issues.amount || issues.merchant) return
    setError(null)
    setDone(null)
    setSubmitting(true)
    const value = parseAmount(amount)!
    const name = merchant.trim()
    try {
      await creditApi.purchase(card.id, { amount: value, merchant: name, installments: n }, idem.keyFor({ card: card.id, value, name, n }))
      idem.settle()
      setAmount('')
      setMerchant('')
      setInstallments(1)
      setDone(`Compra de ${formatBRL(value)} em ${name}${n > 1 ? `, em ${n}x` : ''}, aprovada.`)
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
      <p className="text-sm text-muted">Simula uma compra no crédito: usa o limite agora e entra na fatura. O dinheiro só sai da conta quando você paga a fatura.</p>
      {error && <Alert tone="error">{error}</Alert>}
      {done && <Alert tone="success">{done}</Alert>}
      <Field label="Estabelecimento" htmlFor="credit-merchant" error={errors.merchant} hint="Ex.: Loja de eletrônicos">
        <Input id="credit-merchant" value={merchant} maxLength={MERCHANT_MAX} autoComplete="off" aria-invalid={!!errors.merchant} onChange={(e) => { setMerchant(e.target.value); setErrors((x) => ({ ...x, merchant: undefined })) }} />
      </Field>
      <Field label="Valor (R$)" htmlFor="credit-amount" error={errors.amount} hint={available !== undefined ? `Limite disponível: ${formatBRL(available)}` : undefined}>
        <Input id="credit-amount" inputMode="decimal" className="num" placeholder="0,00" autoComplete="off" value={amount} aria-invalid={!!errors.amount} onChange={(e) => { setAmount(sanitizeAmount(e.target.value)); setErrors((x) => ({ ...x, amount: undefined })) }} />
      </Field>
      <Field label="Parcelas" htmlFor="credit-installments">
        <Select id="credit-installments" value={n} onChange={(e) => setInstallments(Number(e.target.value))}>
          {options.map((o) => (
            <option key={o.n} value={o.n}>{o.label}</option>
          ))}
        </Select>
      </Field>
      <div><Button type="submit" loading={submitting}>Comprar no crédito</Button></div>
    </form>
  )
}

/** Faturas: situação, itens (parcelas) e pagamento total ou parcial. */
export function Invoices({ card, balance, currentMonth, canManage = true, onChanged }: { card: Card; balance?: string; currentMonth?: string; canManage?: boolean; onChanged: () => unknown }) {
  const invoices = useQuery({ queryKey: ['credit-invoices', card.id], queryFn: () => creditApi.invoices(card.id) })
  const [paying, setPaying] = useState<Invoice | null>(null)
  const [splitting, setSplitting] = useState<Invoice | null>(null)
  const [open, setOpen] = useState<string | null>(null)

  return (
    <Panel title="Faturas">
      {invoices.isPending && <Skeleton className="h-24" />}
      {invoices.isError && <ErrorState message={messageFor(invoices.error)} onRetry={() => invoices.refetch()} />}
      {invoices.data && invoices.data.every((i) => Number(i.total.amount) === 0) && (
        <EmptyState title="Nenhuma compra no crédito ainda">As compras parceladas aparecem aqui, fatura por fatura.</EmptyState>
      )}
      <ul className="divide-y divide-line">
        {[...(invoices.data ?? [])]
          .filter((i) => Number(i.total.amount) > 0 || i.month === currentMonth)
          .sort((a, b) => a.month.localeCompare(b.month)) // a mais próxima primeiro
          .map((i) => (
          <li key={i.month} className="py-3">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div>
                <p className="flex items-center gap-2 font-medium">
                  <Receipt size={16} className="text-muted" aria-hidden /> {monthLabel(i.month)}
                  <Badge tone={tone[i.status]}>{currentMonth && i.month > currentMonth ? 'Futura' : invoiceStatusLabel[i.status]}</Badge>
                </p>
                <p className="mt-0.5 text-xs text-muted">{dueHint(i)}</p>
              </div>
              <div className="text-right">
                <p className="num font-medium">{formatBRL(i.total.amount)}</p>
                {Number(i.charges.amount) > 0 && <p className="num text-xs text-danger">com {formatBRL(i.charges.amount)} de multa e juros</p>}
                {Number(i.paid.amount) > 0 && <p className="num text-xs text-muted">pago {formatBRL(i.paid.amount)}</p>}
              </div>
            </div>
            <div className="mt-2 flex flex-wrap gap-2">
              <Button variant="ghost" className="-ml-2 min-h-8 px-2" aria-expanded={open === i.month} onClick={() => setOpen(open === i.month ? null : i.month)}>
                {open === i.month ? <CaretUp size={14} /> : <CaretDown size={14} />} Itens da fatura
              </Button>
              {canManage && Number(i.remaining.amount) > 0 && (
                <Button variant={i.status === 'OVERDUE' ? 'primary' : 'secondary'} className="min-h-8" onClick={() => setPaying(i)}>
                  Pagar fatura
                </Button>
              )}
              {canManage && ['CLOSED', 'OVERDUE', 'REVOLVING'].includes(i.status) && Number(i.remaining.amount) >= 50 && (
                <Button variant="ghost" className="min-h-8" onClick={() => setSplitting(i)}>Parcelar fatura</Button>
              )}
            </div>
            {open === i.month && <InvoiceItems cardId={card.id} month={i.month} />}
          </li>
        ))}
      </ul>
      <PayDialog card={card} invoice={paying} balance={balance} onClose={() => setPaying(null)} onPaid={async () => { setPaying(null); await onChanged() }} />
      <RefinanceDialog card={card} invoice={splitting} onClose={() => setSplitting(null)} onDone={async () => { setSplitting(null); await onChanged() }} />
    </Panel>
  )
}

function InvoiceItems({ cardId, month }: { cardId: string; month: string }) {
  const detail = useQuery({ queryKey: ['credit-invoice', cardId, month], queryFn: () => creditApi.invoice(cardId, month) })
  if (detail.isPending) return <Skeleton className="mt-3 h-16" />
  if (detail.isError) return <p className="mt-3 text-sm text-danger">{messageFor(detail.error)}</p>
  const { items, charges, payments } = detail.data
  return (
    <div className="mt-3 rounded-ui bg-surface-2/60 p-3 text-sm">
      {items.length === 0 && <p className="text-muted">Nenhum item nesta fatura.</p>}
      <ul className="flex flex-col gap-1.5">
        {items.map((it, idx) => (
          <li key={idx} className="flex items-start justify-between gap-3">
            <span>
              {it.merchant}
              {it.installments > 1 && <span className="ml-1 text-muted">{it.number}/{it.installments}</span>}
              <span className="block text-xs text-muted">comprado em {shortDate(it.purchasedOn)}</span>
            </span>
            <span className="num shrink-0">{formatBRL(it.amount.amount)}</span>
          </li>
        ))}
      </ul>
      {charges.length > 0 && (
        <ul className="mt-3 flex flex-col gap-1.5 border-t border-line pt-2 text-danger">
          {charges.map((c) => (
            <li key={c.kind} className="flex justify-between gap-3">
              <span>{chargeLabel[c.kind] ?? c.kind}</span>
              <span className="num">{formatBRL(c.amount.amount)}</span>
            </li>
          ))}
        </ul>
      )}
      {payments.length > 0 && (
        <div className="mt-3 border-t border-line pt-2">
          <p className="mb-1 text-xs uppercase tracking-wider text-muted">Pagamentos</p>
          <ul className="flex flex-col gap-1">
            {payments.map((p, idx) => (
              <li key={idx} className="flex justify-between gap-3 text-ok">
                <span>{formatDateTime(p.createdAt)}{p.kind === 'REFINANCE' ? ' · parcelada' : ''}</span>
                <span className="num">{formatBRL(p.amount.amount)}</span>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  )
}

function PayDialog({ card, invoice, balance, onClose, onPaid }: { card: Card; invoice: Invoice | null; balance?: string; onClose: () => void; onPaid: () => unknown }) {
  return (
    <Dialog open={invoice !== null} onClose={onClose} title={invoice ? `Pagar fatura de ${monthLabel(invoice.month)}` : ''} icon={<Receipt size={22} weight="duotone" />}>
      {invoice && <PayBody key={invoice.month} card={card} invoice={invoice} balance={balance} onClose={onClose} onPaid={onPaid} />}
    </Dialog>
  )
}

function PayBody({ card, invoice, balance, onClose, onPaid }: { card: Card; invoice: Invoice; balance?: string; onClose: () => void; onPaid: () => unknown }) {
  const [idem] = useState(() => createIdempotency())
  const remaining = invoice.remaining.amount
  const minimum = invoice.minimumDue.amount
  const hasMinimum = Number(minimum) > 0 && Number(minimum) < Number(remaining)
  const [mode, setMode] = useState<'all' | 'min' | 'other'>('all')
  const [other, setOther] = useState('')
  const [issue, setIssue] = useState<string | null>(null)
  const pay = useMutation({
    mutationFn: async () => {
      const value = mode === 'all' ? remaining : mode === 'min' ? minimum : parseAmount(other)!
      try {
        const result = await creditApi.payInvoice(card.id, invoice.month, value, idem.keyFor({ card: card.id, month: invoice.month, value }))
        idem.settle()
        return result
      } catch (e) {
        if (!(e instanceof ApiError) || !e.uncertain) idem.settle()
        throw e
      }
    },
    onSuccess: () => onPaid(),
  })
  const lowBalance = balance !== undefined && mode === 'all' && Number(balance) < Number(remaining)

  function submit() {
    if (mode === 'other') {
      const problem = payIssue(other, remaining)
      setIssue(problem)
      if (problem) return
    }
    pay.mutate()
  }

  return (
    <form id="pay-form" className="flex flex-col gap-4" onSubmit={(e) => { e.preventDefault(); submit() }} noValidate>
      <p className="text-sm text-muted">
        O valor sai do saldo da conta {balance !== undefined && <>(<span className="num text-ink">{formatBRL(balance)}</span> agora)</>} e libera limite na hora. Vence em {shortDate(invoice.dueDate)}.
      </p>
      {pay.isError && <Alert tone="error">{messageFor(pay.error)}</Alert>}
      <fieldset className="flex flex-col gap-2" disabled={pay.isPending}>
        <legend className="mb-1 text-sm font-medium">Quanto você quer pagar?</legend>
        {[
          { id: 'all' as const, label: `Fatura inteira, ${formatBRL(remaining)}` },
          ...(hasMinimum ? [{ id: 'min' as const, label: `Pagamento mínimo, ${formatBRL(minimum)}` }] : []),
          { id: 'other' as const, label: 'Outro valor' },
        ].map((o) => (
          <label key={o.id} className={`flex cursor-pointer items-center gap-3 rounded-ui border px-4 py-2.5 text-sm transition ${mode === o.id ? 'border-accent bg-accent/10' : 'border-line hover:border-muted/50'}`}>
            <input type="radio" name="pay-mode" checked={mode === o.id} onChange={() => setMode(o.id)} className="size-4 accent-[var(--accent)]" />
            {o.label}
          </label>
        ))}
      </fieldset>
      {mode === 'other' && (
        <Field label="Valor (R$)" htmlFor="pay-amount" error={issue ?? undefined} hint="O que sobrar continua na fatura. Vencida sem o mínimo pago, ela bloqueia novas compras.">
          <Input id="pay-amount" inputMode="decimal" className="num" placeholder="0,00" autoComplete="off" value={other} aria-invalid={!!issue} onChange={(e) => { setOther(sanitizeAmount(e.target.value)); setIssue(null) }} />
        </Field>
      )}
      {mode === 'min' && <Alert tone="info">O que sobrar fica para a próxima fatura com juros de 10% ao mês (rotativo). Parcelar costuma sair mais barato.</Alert>}
      {Number(invoice.charges.amount) > 0 && <Alert tone="info">Esta fatura já tem {formatBRL(invoice.charges.amount)} de multa e juros por atraso.</Alert>}
      {lowBalance && <Alert tone="info">O saldo da conta é menor que a fatura inteira. Deposite antes ou pague só uma parte.</Alert>}
      <div className="-mx-6 -mb-4 flex flex-wrap justify-end gap-3 border-t border-line bg-surface-2/60 px-6 py-4">
        <Button type="button" variant="secondary" onClick={onClose} disabled={pay.isPending}>Voltar</Button>
        <Button type="submit" loading={pay.isPending}>Pagar</Button>
      </div>
    </form>
  )
}

/** Últimas compras no crédito, com as parcelas. */
export function CreditPurchases({ cardId }: { cardId: string }) {
  const purchases = useQuery({ queryKey: ['credit-purchases', cardId], queryFn: () => creditApi.purchases(cardId) })
  return (
    <Panel title="Compras recentes">
      {purchases.isPending && <Skeleton className="h-24" />}
      {purchases.isError && <ErrorState message={messageFor(purchases.error)} onRetry={() => purchases.refetch()} />}
      {purchases.data?.items.length === 0 && <EmptyState title="Nenhuma compra ainda">As compras no crédito aparecem aqui e nas faturas.</EmptyState>}
      {!!purchases.data?.items.length && (
        <ul className="divide-y divide-line">
          {purchases.data.items.map((p) => (
            <li key={p.id} className="flex items-center justify-between gap-4 py-3">
              <div className="min-w-0">
                <p className="truncate font-medium">{p.merchant}{p.madeWith && <span className="ml-2 text-xs font-normal text-muted">no cartão adicional</span>}</p>
                <p className="text-xs text-muted">{formatDateTime(p.createdAt)}{p.installments > 1 ? ` · ${p.installments}x de ${formatBRL(p.installmentAmount.amount)}` : ' · à vista'}</p>
              </div>
              <p className="num shrink-0 font-medium">{formatBRL(p.total.amount)}</p>
            </li>
          ))}
        </ul>
      )}
    </Panel>
  )
}

/** Parcelar o que falta de uma fatura fechada, com juros (a tabela vem do servidor). */
function RefinanceDialog({ card, invoice, onClose, onDone }: { card: Card; invoice: Invoice | null; onClose: () => void; onDone: () => unknown }) {
  return (
    <Dialog open={invoice !== null} onClose={onClose} title={invoice ? `Parcelar fatura de ${monthLabel(invoice.month)}` : ''} icon={<Receipt size={22} weight="duotone" />}>
      {invoice && <RefinanceBody key={invoice.month} card={card} invoice={invoice} onClose={onClose} onDone={onDone} />}
    </Dialog>
  )
}

function RefinanceBody({ card, invoice, onClose, onDone }: { card: Card; invoice: Invoice; onClose: () => void; onDone: () => unknown }) {
  const [idem] = useState(() => createIdempotency())
  const offer = useQuery({ queryKey: ['credit-refinance', card.id, invoice.month], queryFn: () => creditApi.refinanceOptions(card.id, invoice.month) })
  const [chosen, setChosen] = useState<RefinancePlan | null>(null)
  const refinance = useMutation({
    mutationFn: async () => {
      try {
        const r = await creditApi.refinance(card.id, invoice.month, chosen!.installments, idem.keyFor({ card: card.id, month: invoice.month, n: chosen!.installments }))
        idem.settle()
        return r
      } catch (e) {
        if (!(e instanceof ApiError) || !e.uncertain) idem.settle()
        throw e
      }
    },
    onSuccess: () => onDone(),
  })
  return (
    <div className="flex flex-col gap-4">
      <p className="text-sm text-muted">
        Você paga {formatBRL(invoice.remaining.amount)} em parcelas, a partir da próxima fatura aberta. A fatura de {monthLabel(invoice.month)} fica quitada, mas as parcelas levam juros e usam limite.
      </p>
      {offer.isPending && <Skeleton className="h-24" />}
      {offer.isError && <Alert tone="error">{messageFor(offer.error)}</Alert>}
      {refinance.isError && <Alert tone="error">{messageFor(refinance.error)}</Alert>}
      {offer.data && (
        <fieldset className="flex flex-col gap-2" disabled={refinance.isPending}>
          <legend className="mb-1 text-sm font-medium">Em quantas vezes?</legend>
          {offer.data.options.map((o) => (
            <label key={o.installments} className={`flex cursor-pointer items-center gap-3 rounded-ui border px-4 py-2.5 text-sm transition ${chosen?.installments === o.installments ? 'border-accent bg-accent/10' : 'border-line hover:border-muted/50'}`}>
              <input type="radio" name="refinance" checked={chosen?.installments === o.installments} onChange={() => setChosen(o)} className="size-4 accent-[var(--accent)]" />
              <span className="flex-1">{o.installments}x de <span className="num font-medium">{formatBRL(o.installment.amount)}</span></span>
              <span className="num text-xs text-muted">total {formatBRL(o.total.amount)} · juros {formatBRL(o.interest.amount)}</span>
            </label>
          ))}
        </fieldset>
      )}
      {offer.data && <p className="text-xs text-muted">Juros de {offer.data.options[0]?.monthlyRate}% ao mês, parcelas fixas.</p>}
      <div className="-mx-6 -mb-4 flex flex-wrap justify-end gap-3 border-t border-line bg-surface-2/60 px-6 py-4">
        <Button type="button" variant="secondary" onClick={onClose} disabled={refinance.isPending}>Voltar</Button>
        <Button type="button" loading={refinance.isPending} disabled={!chosen} onClick={() => refinance.mutate()}>Parcelar</Button>
      </div>
    </div>
  )
}

/** O cliente muda o limite do próprio cartão: reduz à vontade, aumenta até R$ 10.000,00 se está em dia. */
export function LimitPanel({ card, summary, onDone }: { card: Card; summary: CreditSummary; onDone: () => unknown }) {
  const [value, setValue] = useState('')
  const [issue, setIssue] = useState<string | null>(null)
  const [done, setDone] = useState<string | null>(null)
  const change = useMutation({
    mutationFn: (amount: string) => creditApi.changeLimit(card.id, amount),
    onSuccess: async (s) => {
      setDone(`Novo limite: ${formatBRL(s.creditLimit.amount)}.`)
      setValue('')
      await onDone()
    },
  })
  function submit() {
    const problem = limitIssue(value, summary.used.amount)
    setIssue(problem)
    setDone(null)
    if (!problem) change.mutate(parseAmount(value)!)
  }
  return (
    <Panel title="Limite do cartão">
      <form className="flex flex-col gap-4" onSubmit={(e) => { e.preventDefault(); submit() }} noValidate>
        <p className="text-sm text-muted">
          Limite atual: <span className="num text-ink">{formatBRL(summary.creditLimit.amount)}</span>. Reduzir vale na hora (nunca abaixo do que está em uso). Aumentar vai até R$ 10.000,00 e só com a fatura em dia; acima disso, fale com o banco.
        </p>
        {change.isError && <Alert tone="error">{messageFor(change.error)}</Alert>}
        {done && <Alert tone="success">{done}</Alert>}
        <Field label="Novo limite (R$)" htmlFor="new-limit" error={issue ?? undefined} hint="Múltiplos de R$ 100,00">
          <Input id="new-limit" inputMode="decimal" className="num" placeholder="0,00" autoComplete="off" value={value} aria-invalid={!!issue} onChange={(e) => { setValue(sanitizeAmount(e.target.value)); setIssue(null) }} />
        </Field>
        <div><Button type="submit" loading={change.isPending}>Alterar limite</Button></div>
      </form>
    </Panel>
  )
}

/** Cartão adicional: número próprio, mas gasta o limite e cai na fatura deste cartão. */
export function AdditionalPanel({ card }: { card: Card }) {
  const navigate = useNavigate()
  const [name, setName] = useState('')
  const [issue, setIssue] = useState<string | null>(null)
  const add = useMutation({
    mutationFn: () => creditApi.addAdditional(card.id, name.trim()),
    onSuccess: (c) => navigate(`/cartoes/${c.id}`),
  })
  return (
    <Panel title="Cartão adicional">
      <form className="flex flex-col gap-4" onSubmit={(e) => { e.preventDefault(); const p = holderIssue(name); setIssue(p); if (!p) add.mutate() }} noValidate>
        <p className="text-sm text-muted">
          Peça um cartão com outro nome (até {MAX_ADDITIONAL} por conta, junto com os cartões de crédito dela). Ele usa o mesmo limite e as compras entram na sua fatura. Para cancelar este cartão, cancele os adicionais antes.
        </p>
        {add.isError && <Alert tone="error">{messageFor(add.error)}</Alert>}
        <Field label="Nome no cartão" htmlFor="additional-name" error={issue ?? undefined}>
          <Input id="additional-name" value={name} maxLength={40} autoComplete="off" aria-invalid={!!issue} onChange={(e) => { setName(e.target.value); setIssue(null) }} />
        </Field>
        <div><Button type="submit" loading={add.isPending}>Pedir cartão adicional</Button></div>
      </form>
    </Panel>
  )
}
