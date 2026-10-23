import { useEffect, useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router'
import { CaretLeft, Eye, EyeSlash } from '@phosphor-icons/react'
import { bankingApi } from '../../services/banking'
import { cardApi } from '../../services/cards'
import type { Card, CardDetails } from '../../services/types'
import { ApiError } from '../../lib/api'
import { createIdempotency } from '../../lib/idempotency'
import { messageFor } from '../../lib/errors'
import { accountLabel, formatDateTime } from '../../lib/format'
import { formatBRL, parseAmount } from '../../lib/money'
import { formatCardNumber, formatExpiry, MERCHANT_MAX, purchaseIssues, statusLabel, type ReasonAction } from '../../lib/card'
import { sanitizeAmount } from '../../lib/validation'
import { Alert, Badge, Button, EmptyState, ErrorState, Field, Input, Panel, Skeleton } from '../../components/ui'
import { CopyButton } from '../pix/parts'
import { useAccounts } from '../accounts/hooks'
import { CardActionDialog } from './CardActionDialog'
import { CardVisual } from './CardVisual'
import { useRefreshCards } from './hooks'

/** Quanto tempo o número e o CVV ficam na tela antes de se esconderem sozinhos. */
const REVEAL_SECONDS = 30

export function CardDetailPage() {
  const { id = '' } = useParams()
  const accounts = useAccounts()
  const refresh = useRefreshCards()
  const card = useQuery({ queryKey: ['card', id], queryFn: () => cardApi.get(id) })
  const account = accounts.data?.find((a) => a.id === card.data?.accountId)
  const limits = useQuery({
    queryKey: ['limits', card.data?.accountId],
    queryFn: () => bankingApi.limits(card.data!.accountId),
    enabled: !!card.data,
  })
  const limit = limits.data?.find((l) => l.type === 'CARD')
  const [details, setDetails] = useState<CardDetails | null>(null)

  if (card.isPending) return <Skeleton className="h-64" />
  if (card.isError) return <ErrorState message={messageFor(card.error)} onRetry={() => card.refetch()} />
  const c = card.data
  const usable = c.status === 'ACTIVE'

  return (
    <>
      <Link to="/cartoes" className="mb-4 inline-flex items-center gap-1 text-sm text-muted hover:text-ink">
        <CaretLeft size={14} /> Cartões
      </Link>

      <section className="mb-8 grid gap-6 lg:grid-cols-[minmax(0,24rem)_1fr] lg:items-center">
        <CardVisual card={c} details={details} />
        <div className="flex flex-col gap-3">
          <p className="flex flex-wrap items-center gap-2 text-sm text-muted">
            <Badge tone={c.status === 'ACTIVE' ? 'ok' : 'warn'}>{statusLabel[c.status]}</Badge>
            {account ? accountLabel(account) : 'Conta'}
          </p>
          {c.status !== 'ACTIVE' && c.statusReason && (
            <p className="text-sm text-muted">
              Motivo do {c.status === 'BLOCKED' ? 'bloqueio' : 'cancelamento'}: <span className="text-ink">{c.statusReason}</span>
            </p>
          )}
          <dl className="grid gap-4 sm:grid-cols-2">
            <div>
              <dt className="text-xs uppercase tracking-wider text-muted">Saldo da conta</dt>
              <dd className="num mt-1 text-2xl font-semibold tracking-tight">{account ? formatBRL(account.balance.amount) : '—'}</dd>
            </div>
            <div>
              <dt className="text-xs uppercase tracking-wider text-muted">Pode gastar hoje no cartão</dt>
              <dd className="num mt-1 text-2xl font-semibold tracking-tight">{limit ? formatBRL(limit.remainingToday.amount) : '—'}</dd>
              {limit && <dd className="mt-0.5 text-xs text-muted">Até {formatBRL(limit.perOperation.amount)} por compra · {formatBRL(limit.daily.amount)} por dia</dd>}
            </div>
          </dl>
          <p className="text-xs text-muted">Emitido em {formatDateTime(c.createdAt)}.</p>
        </div>
      </section>

      <div className="grid gap-6 lg:grid-cols-2">
        <div className="flex flex-col gap-6">
          <Panel title="Dados do cartão">
            <Reveal card={c} details={details} onDetails={setDetails} />
          </Panel>
          <Panel title="Segurança">
            <Controls card={c} onChanged={async () => { setDetails(null); await refresh() }} />
          </Panel>
        </div>

        <div className="flex flex-col gap-6">
          <Panel title="Comprar no débito (simulado)">
            {usable ? (
              <PurchaseForm key={c.id} card={c} balance={account?.balance.amount} limit={limit} onDone={refresh} />
            ) : (
              <p className="text-sm text-muted">Este cartão está {statusLabel[c.status].toLowerCase()} e não aceita compras.</p>
            )}
          </Panel>
          <Purchases cardId={c.id} />
        </div>
      </div>
    </>
  )
}

function Reveal({ card, details, onDetails }: { card: Card; details: CardDetails | null; onDetails: (d: CardDetails | null) => void }) {
  const [left, setLeft] = useState(0)
  const reveal = useMutation({
    mutationFn: () => cardApi.reveal(card.id),
    onSuccess: (d) => {
      onDetails(d)
      setLeft(REVEAL_SECONDS)
    },
  })

  useEffect(() => {
    if (!details) return
    const timer = setInterval(() => {
      setLeft((s) => {
        if (s <= 1) {
          onDetails(null)
          return 0
        }
        return s - 1
      })
    }, 1000)
    return () => clearInterval(timer)
  }, [details, onDetails])

  if (card.status === 'CANCELED') return <p className="text-sm text-muted">Cartão cancelado: os dados não ficam mais disponíveis.</p>

  if (!details) {
    return (
      <div className="flex flex-col gap-3">
        {reveal.isError && <Alert tone="error">{messageFor(reveal.error)}</Alert>}
        <p className="text-sm text-muted">Número completo, validade e CVV aparecem só aqui, por {REVEAL_SECONDS} segundos. Cada consulta fica registrada na sua conta.</p>
        <div>
          <Button variant="secondary" loading={reveal.isPending} onClick={() => reveal.mutate()}>
            <Eye size={16} /> Mostrar número e CVV
          </Button>
        </div>
      </div>
    )
  }

  return (
    <div className="flex flex-col gap-4">
      <dl className="flex flex-col gap-3 text-sm">
        <div className="flex items-center justify-between gap-3">
          <dt className="text-muted">Número</dt>
          <dd className="num font-medium tracking-wider">{formatCardNumber(details.number)}</dd>
        </div>
        <div className="flex items-center justify-between gap-3">
          <dt className="text-muted">Validade</dt>
          <dd className="num font-medium">{formatExpiry(details.expiryMonth, details.expiryYear)}</dd>
        </div>
        <div className="flex items-center justify-between gap-3">
          <dt className="text-muted">CVV</dt>
          <dd className="num font-medium">{details.cvv}</dd>
        </div>
      </dl>
      <div className="flex flex-wrap items-center gap-3">
        <CopyButton text={details.number} label="Copiar número" />
        <Button variant="ghost" onClick={() => onDetails(null)}>
          <EyeSlash size={16} /> Ocultar
        </Button>
        <span className="num text-xs text-muted" aria-live="off">Some em {left}s</span>
      </div>
    </div>
  )
}

function Controls({ card, onChanged }: { card: Card; onChanged: () => unknown }) {
  const [error, setError] = useState<string | null>(null)
  const [dialog, setDialog] = useState<ReasonAction | null>(null)
  const change = useMutation({
    mutationFn: ({ action, reason }: { action: 'block' | 'unblock' | 'cancel'; reason?: string }) =>
      action === 'unblock' ? cardApi.unblock(card.id) : cardApi[action](card.id, reason ?? ''),
    onSuccess: async () => {
      setError(null)
      setDialog(null)
      await onChanged()
    },
    onError: (e) => setError(messageFor(e)),
  })
  const open = (action: ReasonAction) => {
    setError(null)
    setDialog(action)
  }

  if (card.status === 'CANCELED') return <p className="text-sm text-muted">Este cartão foi cancelado e não volta a funcionar. Para continuar comprando, peça um novo na tela de cartões.</p>

  return (
    <div className="flex flex-col gap-4">
      {error && !dialog && <Alert tone="error">{error}</Alert>}
      {card.status === 'ACTIVE' ? (
        <div>
          <p className="mb-3 text-sm text-muted">Bloqueie se perdeu o controle do cartão. Você desbloqueia quando quiser.</p>
          <Button variant="secondary" onClick={() => open('block')}>
            Bloquear cartão
          </Button>
        </div>
      ) : (
        <div>
          <p className="mb-3 text-sm text-muted">O cartão está bloqueado: nenhuma compra passa até você desbloquear.</p>
          <Button loading={change.isPending && change.variables?.action === 'unblock'} onClick={() => change.mutate({ action: 'unblock' })}>
            Desbloquear cartão
          </Button>
        </div>
      )}
      <div className="border-t border-line pt-4">
        <p className="mb-3 text-sm text-muted">Cancelar é definitivo: o cartão deixa de funcionar e não pode ser reativado. Para voltar a comprar, é só pedir um novo.</p>
        <Button variant="danger" onClick={() => open('cancel')}>
          Cancelar cartão
        </Button>
      </div>
      <CardActionDialog
        card={card}
        action={dialog}
        busy={change.isPending && change.variables?.action !== 'unblock'}
        error={error}
        onClose={() => setDialog(null)}
        onConfirm={(action, reason) => change.mutate({ action, reason })}
      />
    </div>
  )
}

function PurchaseForm({ card, balance, limit, onDone }: { card: Card; balance?: string; limit?: Parameters<typeof purchaseIssues>[3]; onDone: () => unknown }) {
  const [idem] = useState(() => createIdempotency())
  const [amount, setAmount] = useState('')
  const [merchant, setMerchant] = useState('')
  const [errors, setErrors] = useState<{ amount?: string; merchant?: string }>({})
  const [error, setError] = useState<string | null>(null)
  const [done, setDone] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function submit() {
    const issues = purchaseIssues(amount, merchant, balance, limit)
    setErrors(issues)
    if (issues.amount || issues.merchant) return
    setError(null)
    setDone(null)
    setSubmitting(true)
    const value = parseAmount(amount)!
    const name = merchant.trim()
    try {
      await cardApi.purchase(card.id, { amount: value, merchant: name }, idem.keyFor({ card: card.id, value, name }))
      idem.settle()
      setAmount('')
      setMerchant('')
      setDone(`Compra de ${formatBRL(value)} em ${name} aprovada.`)
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
      <p className="text-sm text-muted">Simula uma compra num estabelecimento: o valor sai do saldo da conta na hora.</p>
      {error && <Alert tone="error">{error}</Alert>}
      {done && <Alert tone="success">{done}</Alert>}
      <Field label="Estabelecimento" htmlFor="merchant" error={errors.merchant} hint="Ex.: Mercado Central">
        <Input id="merchant" value={merchant} maxLength={MERCHANT_MAX} autoComplete="off" aria-invalid={!!errors.merchant} onChange={(e) => { setMerchant(e.target.value); setErrors((x) => ({ ...x, merchant: undefined })) }} />
      </Field>
      <Field label="Valor (R$)" htmlFor="purchase-amount" error={errors.amount} hint={balance !== undefined ? `Saldo da conta: ${formatBRL(balance)}` : undefined}>
        <Input id="purchase-amount" inputMode="decimal" className="num" placeholder="0,00" autoComplete="off" value={amount} aria-invalid={!!errors.amount} onChange={(e) => { setAmount(sanitizeAmount(e.target.value)); setErrors((x) => ({ ...x, amount: undefined })) }} />
      </Field>
      <div><Button type="submit" loading={submitting}>Comprar</Button></div>
    </form>
  )
}

function Purchases({ cardId }: { cardId: string }) {
  const purchases = useQuery({ queryKey: ['card-purchases', cardId], queryFn: () => cardApi.purchases(cardId) })
  return (
    <Panel title="Compras recentes">
      {purchases.isPending && <Skeleton className="h-24" />}
      {purchases.isError && <ErrorState message={messageFor(purchases.error)} onRetry={() => purchases.refetch()} />}
      {purchases.data?.items.length === 0 && <EmptyState title="Nenhuma compra ainda">As compras feitas com este cartão aparecem aqui e no extrato.</EmptyState>}
      {!!purchases.data?.items.length && (
        <ul className="divide-y divide-line">
          {purchases.data.items.map((p) => (
            <li key={p.id} className="flex items-center justify-between gap-4 py-3">
              <div className="min-w-0">
                <p className="truncate font-medium">{p.merchant}</p>
                <p className="text-xs text-muted">{formatDateTime(p.createdAt)}</p>
              </div>
              <p className="num shrink-0 font-medium">{formatBRL(p.amount.amount)}</p>
            </li>
          ))}
        </ul>
      )}
    </Panel>
  )
}
