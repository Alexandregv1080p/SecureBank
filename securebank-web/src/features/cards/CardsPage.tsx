import { useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { Link, useNavigate } from 'react-router'
import { cardApi } from '../../services/cards'
import { messageFor } from '../../lib/errors'
import { accountLabel } from '../../lib/format'
import { MAX_OPEN_PER_ACCOUNT, statusLabel } from '../../lib/card'
import { Alert, Badge, Button, EmptyState, ErrorState, PageHeader, Panel, Skeleton } from '../../components/ui'
import { useAccounts } from '../accounts/hooks'
import { AccountSelect } from '../pix/parts'
import { CardVisual } from './CardVisual'
import { useRefreshCards } from './hooks'

/** Cartões de débito virtuais: a compra sai direto do saldo da conta, sem fatura. */
export function CardsPage() {
  const cards = useQuery({ queryKey: ['cards'], queryFn: cardApi.list })
  const accounts = useAccounts()
  const list = cards.data ?? []

  return (
    <>
      <PageHeader title="Cartões" description="Cartão de débito virtual: a compra sai direto do saldo da conta, sem fatura e sem juros." />

      {cards.isPending && <Skeleton className="h-56" />}
      {cards.isError && <ErrorState message={messageFor(cards.error)} onRetry={() => cards.refetch()} />}

      {list.length > 0 && (
        <ul className="mb-8 grid gap-6 sm:grid-cols-2 xl:grid-cols-3">
          {list.map((c, i) => {
            const account = accounts.data?.find((a) => a.id === c.accountId)
            return (
              <li key={c.id} className="rise" style={{ '--i': i } as React.CSSProperties}>
                <Link to={`/cartoes/${c.id}`} className="block rounded-2xl transition hover:-translate-y-0.5" aria-label={`Abrir cartão final ${c.last4}`}>
                  <CardVisual card={c} />
                </Link>
                <p className="mt-3 flex flex-wrap items-center gap-2 text-sm text-muted">
                  {c.status !== 'ACTIVE' && <Badge tone="warn">{statusLabel[c.status]}</Badge>}
                  {account ? accountLabel(account) : 'Conta'}
                </p>
              </li>
            )
          })}
        </ul>
      )}

      {cards.data?.length === 0 && !accounts.isPending && (
        <div className="mb-8">
          <EmptyState title="Você ainda não tem cartão">
            Peça um cartão de débito virtual abaixo. Você vê o número e o CVV na hora e pode bloquear quando quiser.
          </EmptyState>
        </div>
      )}

      <div className="max-w-xl">
        <NewCard accountsLoading={accounts.isPending} />
      </div>
    </>
  )
}

function NewCard({ accountsLoading }: { accountsLoading: boolean }) {
  const accounts = useAccounts()
  const cards = useQuery({ queryKey: ['cards'], queryFn: cardApi.list })
  const navigate = useNavigate()
  const refresh = useRefreshCards()
  const [accountId, setAccountId] = useState('')
  const [error, setError] = useState<string | undefined>()
  const active = (accounts.data ?? []).filter((a) => a.status === 'ACTIVE')
  const effective = accountId || (active.length === 1 ? active[0].id : '')
  const openOn = (id: string) => (cards.data ?? []).filter((c) => c.accountId === id).length
  const issue = useMutation({
    mutationFn: () => cardApi.issue(effective),
    onSuccess: async (card) => {
      await refresh()
      navigate(`/cartoes/${card.id}`)
    },
  })

  if (accountsLoading) return <Skeleton className="h-40" />
  if (!active.length) {
    return (
      <EmptyState title="Você precisa de uma conta ativa" action={<Link to="/contas"><Button>Ir para Contas</Button></Link>}>
        O cartão fica ligado a uma conta. Abra uma para pedir o cartão.
      </EmptyState>
    )
  }
  const full = !!effective && openOn(effective) >= MAX_OPEN_PER_ACCOUNT

  return (
    <Panel title="Pedir novo cartão">
      <form
        className="flex flex-col gap-4"
        onSubmit={(e) => {
          e.preventDefault()
          if (!effective) return setError('Escolha a conta do cartão')
          setError(undefined)
          issue.mutate()
        }}
        noValidate
      >
        {issue.isError && <Alert tone="error">{messageFor(issue.error)}</Alert>}
        <ul className="flex flex-col gap-1.5 text-sm text-muted">
          <li>Número, validade e CVV aparecem na hora, só para você.</li>
          <li>A compra sai do saldo da conta e respeita o limite do cartão.</li>
          <li>Até {MAX_OPEN_PER_ACCOUNT} cartões por conta. Bloqueie e desbloqueie quando quiser.</li>
        </ul>
        {active.length > 1 && <AccountSelect id="card-account" label="Conta do cartão" accounts={active} value={effective} onChange={setAccountId} error={error} />}
        {active.length === 1 && <p className="text-sm">Conta: <span className="font-medium">{accountLabel(active[0])}</span></p>}
        {full && <Alert tone="info">Esta conta já tem {MAX_OPEN_PER_ACCOUNT} cartões. Cancele um para pedir outro.</Alert>}
        <div><Button type="submit" loading={issue.isPending} disabled={full}>Pedir cartão</Button></div>
      </form>
    </Panel>
  )
}
