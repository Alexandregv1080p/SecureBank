import { useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { CheckCircle } from '@phosphor-icons/react'
import { Link } from 'react-router'
import { bankingApi } from '../../services/banking'
import type { Account } from '../../services/types'
import { ApiError } from '../../lib/api'
import { createIdempotency } from '../../lib/idempotency'
import { formatBRL } from '../../lib/money'
import { accountTypeLabel } from '../../lib/format'
import { messageFor } from '../../lib/errors'
import { Alert, Button, Panel, Skeleton } from '../../components/ui'
import { CopyButton } from '../pix/parts'
import { useRefreshMoney } from './hooks'

type Type = 'CHECKING' | 'SAVINGS'

/** O que cada tipo é, sem prometer o que o banco não faz: hoje as duas contas funcionam do mesmo jeito. */
const options: { type: Type; title: string; text: string }[] = [
  { type: 'CHECKING', title: 'Conta corrente', text: 'Para o dia a dia: receber, transferir, pagar e usar o Pix.' },
  { type: 'SAVINGS', title: 'Poupança', text: 'Para separar o dinheiro do que você usa no dia a dia.' },
]

const limitNames: Record<string, string> = { WITHDRAW: 'Saque', TRANSFER: 'Transferência', PAYMENT: 'Pagamento', PIX: 'Pix', FX: 'Câmbio' }

/**
 * Abrir conta: escolher o tipo (com a explicação), abrir e ver o resumo (agência, número para copiar, limites iniciais).
 * A Idempotency-Key evita duas contas num toque duplo ou numa nova tentativa depois de a rede cair.
 */
export function OpenAccount() {
  const refresh = useRefreshMoney()
  const [idem] = useState(() => createIdempotency())
  const [type, setType] = useState<Type>('CHECKING')
  const [opened, setOpened] = useState<Account | null>(null)
  const open = useMutation({
    mutationFn: async () => {
      try {
        const account = await bankingApi.openAccount(type, idem.keyFor({ type }))
        idem.settle()
        return account
      } catch (e) {
        if (!(e instanceof ApiError) || !e.uncertain) idem.settle()
        throw e
      }
    },
    onSuccess: async (account) => {
      setOpened(account)
      await refresh()
    },
  })

  if (opened) {
    return (
      <Opened
        account={opened}
        onAnother={() => {
          setOpened(null)
          open.reset()
        }}
      />
    )
  }

  return (
    <Panel title="Abrir nova conta">
      <div className="flex flex-col gap-4">
        {open.isError && <Alert tone="error">{messageFor(open.error)}</Alert>}
        <div role="radiogroup" aria-label="Tipo de conta" className="flex flex-col gap-3">
          {options.map((o) => (
            <button
              key={o.type}
              type="button"
              role="radio"
              aria-checked={type === o.type}
              onClick={() => setType(o.type)}
              className={`rounded-ui border p-4 text-left transition ${type === o.type ? 'border-accent bg-accent/10' : 'border-line hover:border-muted/50'}`}
            >
              <span className="block font-medium">{o.title}</span>
              <span className="mt-1 block text-sm text-muted">{o.text}</span>
            </button>
          ))}
        </div>
        <p className="text-xs text-muted">
          Hoje as duas funcionam do mesmo jeito e começam com os mesmos limites. Para o dinheiro render, use os{' '}
          <Link to="/investimentos" className="underline underline-offset-2">
            Investimentos
          </Link>
          . O número da conta é gerado ao abrir.
        </p>
        <Button loading={open.isPending} onClick={() => open.mutate()}>
          Abrir {accountTypeLabel(type).toLowerCase()}
        </Button>
      </div>
    </Panel>
  )
}

function Opened({ account, onAnother }: { account: Account; onAnother: () => void }) {
  const limits = useQuery({ queryKey: ['limits', account.id], queryFn: () => bankingApi.limits(account.id) })
  return (
    <Panel>
      <div className="flex flex-col gap-4">
        <div className="text-center">
          <CheckCircle size={40} weight="duotone" className="mx-auto text-ok" />
          <h2 className="mt-2 text-lg font-semibold">{accountTypeLabel(account.type)} aberta</h2>
        </div>
        <dl className="flex flex-col gap-2 text-sm">
          <div className="flex justify-between gap-6">
            <dt className="text-muted">Agência</dt>
            <dd className="num">{account.branch}</dd>
          </div>
          <div className="flex justify-between gap-6">
            <dt className="text-muted">Conta</dt>
            <dd className="num font-medium">{account.accountNumber}</dd>
          </div>
          <div className="flex justify-between gap-6">
            <dt className="text-muted">Saldo</dt>
            <dd className="num">{formatBRL(account.balance.amount)}</dd>
          </div>
        </dl>
        <CopyButton text={`Ag. ${account.branch}, conta ${account.accountNumber}`} label="Copiar agência e conta" />
        <div>
          <h3 className="mb-2 text-sm font-medium">Limites iniciais</h3>
          {limits.isPending && <Skeleton className="h-20" />}
          {limits.data && (
            <ul className="flex flex-col gap-1 text-sm">
              {limits.data.map((l) => (
                <li key={l.type} className="flex justify-between gap-4">
                  <span className="text-muted">{limitNames[l.type] ?? l.type}</span>
                  <span className="num">
                    até {formatBRL(l.perOperation.amount)} por vez, {formatBRL(l.daily.amount)} por dia
                  </span>
                </li>
              ))}
            </ul>
          )}
        </div>
        <div className="flex flex-wrap gap-3">
          <Link to={`/contas/${account.id}`}>
            <Button>Ver conta</Button>
          </Link>
          <Button variant="secondary" onClick={onAnother}>
            Abrir outra
          </Button>
        </div>
      </div>
    </Panel>
  )
}
