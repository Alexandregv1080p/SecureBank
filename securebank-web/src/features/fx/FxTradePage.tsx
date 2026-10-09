import { useEffect, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router'
import { CaretLeft } from '@phosphor-icons/react'
import { bankingApi } from '../../services/banking'
import { fxApi, type FxOperation } from '../../services/fx'
import { ApiError } from '../../lib/api'
import { createIdempotency } from '../../lib/idempotency'
import { formatDateTime } from '../../lib/format'
import { messageFor } from '../../lib/errors'
import { addAmounts, formatBRL, parseAmount, subtractAmounts } from '../../lib/money'
import { balanceIssue, limitIssue, sanitizeAmount } from '../../lib/validation'
import { amountError, buyCost, currencyName, formatForeign, rateLabel, sellProceeds } from '../../lib/fx'
import { Alert, Button, EmptyState, ErrorState, Field, Input, Panel, Skeleton } from '../../components/ui'
import { useAccounts } from '../accounts/hooks'
import { AccountSelect, Receipt } from '../pix/parts'
import { useRefreshFx } from './hooks'

/** Quanto tempo a cotação da revisão vale antes de ser conferida de novo. */
const QUOTE_SECONDS = 30

/**
 * Comprar ou vender uma moeda: formulário → revisão (cotação conferida de novo, total, saldo e carteira depois) → comprovante.
 * O servidor recebe a cotação que a pessoa VIU: se mudou no meio do caminho, recusa (FX_RATE_CHANGED), a tela mostra a nova e
 * a pessoa decide de novo, em vez de executar por um preço que ela não aceitou.
 */
export function FxTradePage() {
  const { side = 'comprar', currency = '' } = useParams()
  const buying = side !== 'vender'
  const code = currency.toUpperCase()
  const accounts = useAccounts()
  const rates = useQuery({ queryKey: ['fx', 'rates'], queryFn: fxApi.rates, staleTime: 0 })
  const wallets = useQuery({ queryKey: ['fx-wallets'], queryFn: fxApi.wallets })
  const refresh = useRefreshFx()
  const [idem] = useState(() => createIdempotency())
  const [accountId, setAccountId] = useState('')
  const [amount, setAmount] = useState('')
  const [errors, setErrors] = useState<{ amount?: string; account?: string }>({})
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [done, setDone] = useState<FxOperation | null>(null)
  // revisão: a cotação que a pessoa está vendo e até quando ela vale
  const [review, setReview] = useState<{ rate: string; expiresAt: number } | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [now, setNow] = useState(() => Date.now())

  const list = accounts.data ?? []
  const effectiveAccount = accountId || (list.length === 1 ? list[0].id : '')
  const account = list.find((a) => a.id === effectiveAccount)
  const limits = useQuery({ queryKey: ['limits', effectiveAccount], queryFn: () => bankingApi.limits(effectiveAccount), enabled: !!effectiveAccount })
  const rate = rates.data?.find((r) => r.currency === code)
  const wallet = wallets.data?.find((w) => w.currency === code)?.balance.amount ?? '0.00'
  const appliedFor = (r: { buyRate: string; sellRate: string }) => (buying ? r.buyRate : r.sellRate)
  const applied = rate ? appliedFor(rate) : null
  const shownRate = review?.rate ?? applied
  const estimate = shownRate ? (buying ? buyCost(amount, shownRate) : sellProceeds(amount, shownRate)) : null

  // enquanto a revisão está aberta, o relógio anda; ao zerar, a cotação é conferida de novo (e a pessoa é avisada se mudou)
  useEffect(() => {
    if (!review) return
    const tick = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(tick)
  }, [review])
  const secondsLeft = review ? Math.max(0, Math.ceil((review.expiresAt - now) / 1000)) : 0
  const expired = !!review && secondsLeft === 0
  useEffect(() => {
    if (!expired || submitting) return
    void (async () => {
      const fresh = (await rates.refetch()).data?.find((r) => r.currency === code)
      if (!fresh || !review) return
      const next = appliedFor(fresh)
      if (next !== review.rate) setNotice(`A cotação mudou de R$ ${rateLabel(review.rate)} para R$ ${rateLabel(next)}. Confira o novo valor.`)
      setReview({ rate: next, expiresAt: Date.now() + QUOTE_SECONDS * 1000 })
      setNow(Date.now())
    })()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [expired])

  /** Valida o formulário e abre a revisão com a cotação mais recente. */
  async function goReview() {
    const brlCost = buying && estimate ? estimate : null // o que sai da conta em reais
    const problem =
      amountError(amount, buying ? undefined : wallet) ??
      (brlCost
        ? balanceIssue(brlCost, account?.balance.amount ?? '0') ?? limitIssue(brlCost, limits.data?.find((l) => l.type === 'FX'))
        : null)
    const accountProblem = effectiveAccount ? undefined : 'Escolha a conta'
    setErrors({ amount: problem ?? undefined, account: accountProblem })
    if (problem || accountProblem || !applied) return
    setError(null)
    setNotice(null)
    setSubmitting(true)
    try {
      const fresh = (await rates.refetch()).data?.find((r) => r.currency === code)
      const current = fresh ? appliedFor(fresh) : applied
      if (current !== applied) setNotice(`A cotação mudou de R$ ${rateLabel(applied)} para R$ ${rateLabel(current)}. Confira o novo valor.`)
      setReview({ rate: current, expiresAt: Date.now() + QUOTE_SECONDS * 1000 })
      setNow(Date.now())
    } catch (e) {
      setError(messageFor(e))
    } finally {
      setSubmitting(false)
    }
  }

  async function confirm() {
    if (!review) return
    setError(null)
    setSubmitting(true)
    const body = { accountId: effectiveAccount, currency: code, amount: parseAmount(amount)!, quotedRate: review.rate }
    try {
      const op = await fxApi.trade(buying ? 'buy' : 'sell', body, idem.keyFor({ buying, ...body }))
      idem.settle()
      setDone(op)
      await refresh()
    } catch (e) {
      if (!(e instanceof ApiError) || !e.uncertain) idem.settle()
      setError(messageFor(e))
      if (e instanceof ApiError && e.code === 'FX_RATE_CHANGED') {
        // mostra a cotação nova para decidir de novo
        const fresh = (await rates.refetch()).data?.find((r) => r.currency === code)
        if (fresh) {
          setNotice(`A cotação mudou para R$ ${rateLabel(appliedFor(fresh))}. Confira o novo valor.`)
          setReview({ rate: appliedFor(fresh), expiresAt: Date.now() + QUOTE_SECONDS * 1000 })
          setNow(Date.now())
        }
      }
    } finally {
      setSubmitting(false)
    }
  }

  if (rates.isPending || accounts.isPending) return <Skeleton className="h-64" />
  if (rates.isError) return <ErrorState message={messageFor(rates.error)} onRetry={() => rates.refetch()} />
  if (!rate) return <EmptyState title="Moeda não encontrada" action={<Link to="/cambio"><Button>Voltar ao câmbio</Button></Link>} />
  if (!list.length) return <EmptyState title="Você precisa de uma conta" action={<Link to="/contas"><Button>Abrir conta</Button></Link>} />

  if (done) {
    return (
      <Receipt
        title={buying ? 'Compra realizada' : 'Venda realizada'}
        amount={done.brlAmount.amount}
        lines={[
          { label: buying ? 'Você comprou' : 'Você vendeu', value: formatForeign(done.foreignAmount.currency, done.foreignAmount.amount) },
          { label: 'Cotação aplicada', value: `R$ ${rateLabel(done.rate)}` },
          { label: buying ? 'Você pagou' : 'Você recebeu', value: formatBRL(done.brlAmount.amount) },
          { label: 'Data', value: formatDateTime(done.createdAt) },
          { label: 'Identificador', value: <span className="num text-xs">{done.id}</span> },
        ]}
      >
        <Link to="/cambio"><Button>Concluir</Button></Link>
      </Receipt>
    )
  }

  const value = parseAmount(amount)
  const brl = estimate

  return (
    <>
      <Link to="/cambio" className="mb-4 inline-flex items-center gap-1 text-sm text-muted hover:text-ink">
        <CaretLeft size={14} /> Câmbio
      </Link>
      <div className="grid gap-6 lg:grid-cols-[1fr_22rem]">
        <div className="max-w-xl">
          <h1 className="text-2xl font-semibold tracking-tight">{buying ? 'Comprar' : 'Vender'} {currencyName(code)}</h1>
          <p className="mt-1 text-sm text-muted">
            1 {code} = <span className="num font-medium text-ink">R$ {rateLabel(shownRate!)}</span> · {buying ? 'cotação de compra' : 'cotação de venda'}
          </p>

          {review && value && brl ? (
            <div className="mt-8">
              <Panel title="Confirme a operação">
                {notice && <div className="mb-4"><Alert tone="info">{notice}</Alert></div>}
                <dl className="flex flex-col gap-4 text-sm">
                  <ReviewRow label={buying ? 'Você compra' : 'Você vende'} value={<span className="num text-lg font-semibold">{formatForeign(code, value)}</span>} />
                  <ReviewRow
                    label="Cotação"
                    value={
                      <>
                        <span className="num block">R$ {rateLabel(review.rate)} por {code}</span>
                        <span className="num block text-muted">comercial R$ {rateLabel(rate.mid)} · spread de {rate.spreadPercent.replace('.', ',')}%</span>
                      </>
                    }
                  />
                  <ReviewRow label={buying ? 'Você paga' : 'Você recebe'} value={<span className="num text-lg font-semibold">{formatBRL(brl)}</span>} />
                  {account && <ReviewRow label={buying ? 'Sai da conta' : 'Entra na conta'} value={`${account.accountNumber}`} />}
                  {account && (
                    <ReviewRow
                      label="Saldo depois"
                      value={<span className="num">{formatBRL(buying ? subtractAmounts(account.balance.amount, brl) : addAmounts(account.balance.amount, brl))}</span>}
                    />
                  )}
                  <ReviewRow
                    label="Carteira depois"
                    value={<span className="num">{formatForeign(code, buying ? addAmounts(wallet, value) : subtractAmounts(wallet, value))}</span>}
                  />
                </dl>
                <p className="mt-5 text-xs text-muted">
                  {secondsLeft > 0 ? `Cotação válida por mais ${secondsLeft} s; depois conferimos de novo.` : 'Conferindo a cotação…'} Se ela mudar antes de você confirmar, a operação é recusada.
                </p>
                {error && <div className="mt-5"><Alert tone="error">{error}</Alert></div>}
                <div className="mt-6 flex gap-3">
                  <Button loading={submitting} disabled={expired} onClick={confirm}>{buying ? 'Confirmar compra' : 'Confirmar venda'}</Button>
                  <Button variant="secondary" disabled={submitting} onClick={() => { setReview(null); setNotice(null); setError(null) }}>Editar</Button>
                </div>
              </Panel>
            </div>
          ) : (
            <form className="mt-8 flex flex-col gap-5" onSubmit={(e) => { e.preventDefault(); void goReview() }} noValidate>
              {error && <Alert tone="error">{error}</Alert>}
              {list.length > 1 && <AccountSelect id="account" label={buying ? 'Pagar com a conta' : 'Receber na conta'} accounts={list} value={effectiveAccount} onChange={setAccountId} error={errors.account} />}
              <Field
                label={`Quantidade (${code})`}
                htmlFor="amount"
                error={errors.amount}
                hint={buying ? undefined : `Na carteira: ${formatForeign(code, wallet)}`}
              >
                <Input id="amount" inputMode="decimal" className="num" placeholder="0,00" autoComplete="off" value={amount} aria-invalid={!!errors.amount} onChange={(e) => { setAmount(sanitizeAmount(e.target.value)); setErrors({}) }} />
              </Field>
              {!buying && <div><Button type="button" variant="ghost" onClick={() => setAmount(wallet.replace('.', ','))}>Vender tudo</Button></div>}
              {estimate && <p className="text-lg font-medium">{buying ? 'Você paga' : 'Você recebe'} <span className="num">{formatBRL(estimate)}</span></p>}
              <div className="flex gap-3">
                <Button type="submit" loading={submitting}>Revisar</Button>
                <Link to="/cambio"><Button type="button" variant="secondary" disabled={submitting}>Cancelar</Button></Link>
              </div>
            </form>
          )}
        </div>

        <Panel title="Como funciona">
          <ul className="flex flex-col gap-3 text-sm text-muted">
            <li>Você compra pela cotação comercial mais o spread de {rate.spreadPercent.replace('.', ',')}% e vende pela comercial menos o spread; a diferença fica com o banco.</li>
            <li>Na revisão, a cotação é conferida de novo e vale por {QUOTE_SECONDS} segundos; depois é conferida outra vez, e você é avisado se mudou.</li>
            <li>A compra conta no limite diário de câmbio da conta.</li>
            <li>Tudo é simulado: não há dinheiro nem moeda de verdade.</li>
          </ul>
        </Panel>
      </div>
    </>
  )
}

function ReviewRow({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className="flex items-baseline justify-between gap-6 border-b border-line pb-4 last:border-b-0 last:pb-0">
      <dt className="text-muted">{label}</dt>
      <dd className="text-right">{value}</dd>
    </div>
  )
}
