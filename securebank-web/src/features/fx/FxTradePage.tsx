import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router'
import { CaretLeft } from '@phosphor-icons/react'
import { fxApi, type FxOperation } from '../../services/fx'
import { ApiError } from '../../lib/api'
import { createIdempotency } from '../../lib/idempotency'
import { formatDateTime } from '../../lib/format'
import { messageFor } from '../../lib/errors'
import { formatBRL, parseAmount } from '../../lib/money'
import { amountError, buyCost, currencyName, formatForeign, rateLabel, sellProceeds } from '../../lib/fx'
import { Alert, Button, EmptyState, ErrorState, Field, Input, Panel, Skeleton } from '../../components/ui'
import { useAccounts } from '../accounts/hooks'
import { AccountSelect, Receipt } from '../pix/parts'
import { useRefreshFx } from './hooks'

/**
 * Comprar ou vender uma moeda. O servidor recebe a cotação que a pessoa VIU: se mudou no meio do caminho, recusa
 * (FX_RATE_CHANGED), a tela mostra a nova e a pessoa decide de novo, em vez de executar por um preço que ela não aceitou.
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

  const list = accounts.data ?? []
  const effectiveAccount = accountId || (list.length === 1 ? list[0].id : '')
  const rate = rates.data?.find((r) => r.currency === code)
  const wallet = wallets.data?.find((w) => w.currency === code)?.balance.amount ?? '0.00'
  const applied = rate ? (buying ? rate.buyRate : rate.sellRate) : null
  const estimate = applied ? (buying ? buyCost(amount, applied) : sellProceeds(amount, applied)) : null

  async function confirm() {
    const problem = amountError(amount, buying ? undefined : wallet)
    const accountProblem = effectiveAccount ? undefined : 'Escolha a conta'
    setErrors({ amount: problem ?? undefined, account: accountProblem })
    if (problem || accountProblem || !applied) return
    setError(null)
    setSubmitting(true)
    const body = { accountId: effectiveAccount, currency: code, amount: parseAmount(amount)!, quotedRate: applied }
    try {
      const op = await fxApi.trade(buying ? 'buy' : 'sell', body, idem.keyFor({ buying, ...body }))
      idem.settle()
      setDone(op)
      await refresh()
    } catch (e) {
      if (!(e instanceof ApiError) || !e.uncertain) idem.settle()
      setError(messageFor(e))
      if (e instanceof ApiError && e.code === 'FX_RATE_CHANGED') void rates.refetch() // mostra a cotação nova para decidir de novo
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

  return (
    <>
      <Link to="/cambio" className="mb-4 inline-flex items-center gap-1 text-sm text-muted hover:text-ink">
        <CaretLeft size={14} /> Câmbio
      </Link>
      <div className="grid gap-6 lg:grid-cols-[1fr_22rem]">
        <div className="max-w-xl">
          <h1 className="text-2xl font-semibold tracking-tight">{buying ? 'Comprar' : 'Vender'} {currencyName(code)}</h1>
          <p className="mt-1 text-sm text-muted">
            1 {code} = <span className="num font-medium text-ink">R$ {rateLabel(applied!)}</span> · {buying ? 'cotação de compra' : 'cotação de venda'}
          </p>

          <form className="mt-8 flex flex-col gap-5" onSubmit={(e) => { e.preventDefault(); void confirm() }} noValidate>
            {error && <Alert tone="error">{error}</Alert>}
            {list.length > 1 && <AccountSelect id="account" label={buying ? 'Pagar com a conta' : 'Receber na conta'} accounts={list} value={effectiveAccount} onChange={setAccountId} error={errors.account} />}
            <Field
              label={`Quantidade (${code})`}
              htmlFor="amount"
              error={errors.amount}
              hint={buying ? undefined : `Na carteira: ${formatForeign(code, wallet)}`}
            >
              <Input id="amount" inputMode="decimal" className="num" placeholder="0,00" autoComplete="off" value={amount} aria-invalid={!!errors.amount} onChange={(e) => { setAmount(e.target.value); setErrors({}) }} />
            </Field>
            {!buying && <div><Button type="button" variant="ghost" onClick={() => setAmount(wallet.replace('.', ','))}>Vender tudo</Button></div>}
            {estimate && <p className="text-lg font-medium">{buying ? 'Você paga' : 'Você recebe'} <span className="num">{formatBRL(estimate)}</span></p>}
            <div className="flex gap-3">
              <Button type="submit" loading={submitting}>{buying ? 'Comprar' : 'Vender'}</Button>
              <Link to="/cambio"><Button type="button" variant="secondary" disabled={submitting}>Cancelar</Button></Link>
            </div>
          </form>
        </div>

        <Panel title="Como funciona">
          <ul className="flex flex-col gap-3 text-sm text-muted">
            <li>Você compra pela cotação comercial mais o spread de {rate.spreadPercent.replace('.', ',')}% e vende pela comercial menos o spread; a diferença fica com o banco.</li>
            <li>Se a cotação mudar antes de você confirmar, a operação é recusada e a tela mostra a nova, para você decidir de novo.</li>
            <li>A compra conta no limite diário de câmbio da conta.</li>
            <li>Tudo é simulado: não há dinheiro nem moeda de verdade.</li>
          </ul>
        </Panel>
      </div>
    </>
  )
}
