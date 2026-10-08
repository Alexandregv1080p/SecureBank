import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link, useNavigate, useParams } from 'react-router'
import { CaretLeft } from '@phosphor-icons/react'
import { investmentApi } from '../../services/investments'
import { ApiError } from '../../lib/api'
import { createIdempotency } from '../../lib/idempotency'
import { messageFor } from '../../lib/errors'
import { formatBRL, parseAmount } from '../../lib/money'
import { applyError, rateLabel, taxExplanation, termLabel } from '../../lib/investment'
import { Alert, Button, EmptyState, ErrorState, Field, Input, Panel, Skeleton } from '../../components/ui'
import { useAccounts } from '../accounts/hooks'
import { AccountSelect } from '../pix/parts'
import { useRefreshInvestments } from './hooks'

/** Aplicar: mostra as regras do produto, pede conta e valor, revisa e aplica (uma Idempotency-Key por intenção). */
export function ApplyInvestmentPage() {
  const { code = '' } = useParams()
  const navigate = useNavigate()
  const accounts = useAccounts()
  const products = useQuery({ queryKey: ['investments', 'products'], queryFn: investmentApi.products, staleTime: 5 * 60_000 })
  const refresh = useRefreshInvestments()
  const [idem] = useState(() => createIdempotency())
  const [accountId, setAccountId] = useState('')
  const [amount, setAmount] = useState('')
  const [fieldError, setFieldError] = useState<string | null>(null)
  const [accountError, setAccountError] = useState<string | null>(null)
  const [review, setReview] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const list = accounts.data ?? []
  const effectiveAccount = accountId || (list.length === 1 ? list[0].id : '')
  const account = list.find((a) => a.id === effectiveAccount)
  const product = products.data?.find((p) => p.code === code)

  if (products.isPending || accounts.isPending) return <Skeleton className="h-64" />
  if (products.isError) return <ErrorState message={messageFor(products.error)} onRetry={() => products.refetch()} />
  if (!product) return <EmptyState title="Produto não encontrado" action={<Link to="/investimentos"><Button>Ver produtos</Button></Link>} />
  if (!list.length) return <EmptyState title="Você precisa de uma conta para investir" action={<Link to="/contas"><Button>Abrir conta</Button></Link>} />

  function goReview() {
    const problem = applyError(amount, product!.minAmount, account?.balance.amount)
    setFieldError(problem)
    setAccountError(effectiveAccount ? null : 'Escolha a conta de onde sai o dinheiro')
    if (!problem && effectiveAccount) setReview(true)
  }

  async function confirm() {
    setError(null)
    setSubmitting(true)
    const body = { accountId: effectiveAccount, productCode: product!.code, amount: parseAmount(amount)! }
    try {
      const created = await investmentApi.apply(body, idem.keyFor(body))
      idem.settle()
      await refresh()
      navigate(`/investimentos/${created.id}`, { replace: true })
    } catch (e) {
      if (!(e instanceof ApiError) || !e.uncertain) idem.settle()
      setError(messageFor(e))
      setSubmitting(false)
    }
  }

  return (
    <>
      <Link to="/investimentos" className="mb-4 inline-flex items-center gap-1 text-sm text-muted hover:text-ink">
        <CaretLeft size={14} /> Investimentos
      </Link>
      <div className="grid gap-6 lg:grid-cols-[1fr_22rem]">
        <div className="max-w-xl">
          <h1 className="text-2xl font-semibold tracking-tight">Aplicar em {product.name}</h1>
          <p className="mt-1 text-sm text-muted">
            {rateLabel(product.annualRatePercent)} · {termLabel(product.termDays)} · a partir de {formatBRL(product.minAmount)}
          </p>

          {!review ? (
            <form className="mt-8 flex flex-col gap-5" onSubmit={(e) => { e.preventDefault(); goReview() }} noValidate>
              {list.length > 1 && <AccountSelect id="account" label="Aplicar a partir da conta" accounts={list} value={effectiveAccount} onChange={setAccountId} error={accountError ?? undefined} />}
              <Field label="Valor (R$)" htmlFor="amount" error={fieldError ?? undefined} hint={account ? `Saldo da conta: ${formatBRL(account.balance.amount)}` : undefined}>
                <Input id="amount" inputMode="decimal" className="num" placeholder="0,00" autoComplete="off" value={amount} aria-invalid={!!fieldError} onChange={(e) => { setAmount(e.target.value); setFieldError(null) }} />
              </Field>
              <div><Button type="submit">Revisar</Button></div>
            </form>
          ) : (
            <div className="mt-8">
              <Panel title="Confirme a aplicação">
                <dl className="flex flex-col gap-4 text-sm">
                  <div className="flex justify-between"><dt className="text-muted">Valor</dt><dd className="num text-lg font-semibold">{formatBRL(parseAmount(amount)!)}</dd></div>
                  <div className="flex justify-between"><dt className="text-muted">Produto</dt><dd>{product.name}</dd></div>
                  <div className="flex justify-between"><dt className="text-muted">Taxa</dt><dd>{rateLabel(product.annualRatePercent)}</dd></div>
                  <div className="flex justify-between"><dt className="text-muted">Resgate</dt><dd>{product.termDays === null ? 'Quando quiser' : `Só no vencimento (${product.termDays} dias)`}</dd></div>
                  {account && <div className="flex justify-between"><dt className="text-muted">Sai da conta</dt><dd>{account.accountNumber}</dd></div>}
                </dl>
                {error && <div className="mt-5"><Alert tone="error">{error}</Alert></div>}
                <div className="mt-6 flex flex-wrap gap-3">
                  <Button loading={submitting} onClick={confirm}>Confirmar aplicação</Button>
                  <Button variant="secondary" disabled={submitting} onClick={() => { setReview(false); setError(null) }}>Editar</Button>
                </div>
              </Panel>
            </div>
          )}
        </div>

        <Panel title="Como funciona">
          <ul className="flex flex-col gap-3 text-sm text-muted">
            <li>{product.termDays === null ? 'Resgate quando quiser; o rendimento conta por dia completo.' : 'Só resgata no vencimento; o rendimento para nessa data.'}</li>
            <li>{taxExplanation}</li>
            <li>Tudo é simulado: nenhum dinheiro de verdade sai do banco.</li>
          </ul>
        </Panel>
      </div>
    </>
  )
}
