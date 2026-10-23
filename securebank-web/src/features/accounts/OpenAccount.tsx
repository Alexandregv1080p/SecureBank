import { useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { ArrowsLeftRight, CheckCircle, PiggyBank, PixLogo, Wallet } from '@phosphor-icons/react'
import type { Icon } from '@phosphor-icons/react'
import { Link } from 'react-router'
import { bankingApi } from '../../services/banking'
import type { Account } from '../../services/types'
import { ApiError } from '../../lib/api'
import { createIdempotency } from '../../lib/idempotency'
import { formatBRL } from '../../lib/money'
import { accountTypeLabel, formatDateTime } from '../../lib/format'
import { messageFor } from '../../lib/errors'
import { Alert, Badge, Button, Panel, Skeleton } from '../../components/ui'
import { CopyButton } from '../pix/parts'
import { useAccounts, useRefreshMoney } from './hooks'

type Type = 'CHECKING' | 'SAVINGS'

/** Igual ao teto do servidor (AccountApplicationService.MAX_OPEN_ACCOUNTS): encerradas não contam. */
export const MAX_OPEN_ACCOUNTS = 5

/** O que cada tipo é, sem prometer o que o banco não faz: hoje as duas contas funcionam do mesmo jeito. */
const options: { type: Type; title: string; text: string; icon: Icon }[] = [
  { type: 'CHECKING', title: 'Conta corrente', text: 'Para o dia a dia: receber, transferir, pagar e usar o Pix.', icon: Wallet },
  { type: 'SAVINGS', title: 'Poupança', text: 'Para separar o dinheiro do que você usa no dia a dia.', icon: PiggyBank },
]

const limitNames: Record<string, string> = { WITHDRAW: 'Saque', TRANSFER: 'Transferência', PAYMENT: 'Pagamento', PIX: 'Pix', FX: 'Câmbio', CARD: 'Cartão de débito' }

/**
 * Abrir conta: escolher o tipo (com a explicação), ver quantas contas já tem, abrir e ver o resumo (agência, número para
 * copiar, limites iniciais, próximos passos). A Idempotency-Key evita duas contas num toque duplo ou numa nova tentativa
 * depois de a rede cair.
 */
export function OpenAccount() {
  const refresh = useRefreshMoney()
  const accounts = useAccounts()
  const [idem] = useState(() => createIdempotency())
  const [type, setType] = useState<Type>('CHECKING')
  const [opened, setOpened] = useState<Account | null>(null)
  const openCount = accounts.data?.filter((a) => a.status !== 'CLOSED').length ?? 0
  const full = openCount >= MAX_OPEN_ACCOUNTS
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
        openCount={openCount}
        onAnother={() => {
          setOpened(null)
          open.reset()
        }}
      />
    )
  }

  return (
    <Panel title="Abrir nova conta">
      <div className="flex flex-col gap-5">
        {open.isError && <Alert tone="error">{messageFor(open.error)}</Alert>}

        <div>
          <div className="mb-1.5 flex items-center justify-between text-xs">
            <span className="text-muted">Contas abertas</span>
            <span className="num font-medium">
              {accounts.isPending ? '…' : `${openCount} de ${MAX_OPEN_ACCOUNTS}`}
            </span>
          </div>
          <div className="h-1.5 overflow-hidden rounded-full bg-bg" role="progressbar" aria-valuemin={0} aria-valuemax={MAX_OPEN_ACCOUNTS} aria-valuenow={openCount} aria-label="Contas abertas">
            <div className={`h-full rounded-full transition-all ${full ? 'bg-warn' : 'bg-accent'}`} style={{ width: `${Math.min(100, (openCount / MAX_OPEN_ACCOUNTS) * 100)}%` }} />
          </div>
          {full && <p className="mt-2 text-xs text-warn">Você chegou ao máximo de {MAX_OPEN_ACCOUNTS} contas abertas. Encerre uma sem saldo para abrir outra.</p>}
        </div>

        <div role="radiogroup" aria-label="Tipo de conta" className="grid gap-3 sm:grid-cols-2">
          {options.map((o) => {
            const selected = type === o.type
            const Ico = o.icon
            return (
              <button
                key={o.type}
                type="button"
                role="radio"
                aria-checked={selected}
                onClick={() => setType(o.type)}
                className={`relative rounded-ui border p-4 text-left transition ${selected ? 'border-accent bg-accent/10' : 'border-line hover:border-muted/50'}`}
              >
                <Ico size={26} weight={selected ? 'duotone' : 'regular'} className={selected ? 'text-accent' : 'text-muted'} />
                <span className="mt-2 block font-medium">{o.title}</span>
                <span className="mt-1 block text-sm text-muted">{o.text}</span>
                {selected && <CheckCircle size={20} weight="fill" className="absolute right-3 top-3 text-accent" aria-hidden />}
              </button>
            )
          })}
        </div>

        <div className="rounded-ui bg-bg p-4">
          <h3 className="text-sm font-medium">O que acontece ao abrir</h3>
          <ul className="mt-2 flex flex-col gap-1.5 text-sm text-muted">
            <li>Agência <span className="num text-text">0001</span> e um número de conta novo, gerado agora.</li>
            <li>Saldo inicial de <span className="num text-text">{formatBRL('0')}</span>. Você deposita depois, na própria conta.</li>
            <li>
              Limites iniciais por operação e por dia, que você confere no resumo. Para o dinheiro render, use os{' '}
              <Link to="/investimentos" className="underline underline-offset-2">
                Investimentos
              </Link>
              .
            </li>
          </ul>
        </div>

        <Button loading={open.isPending} disabled={full || accounts.isPending} onClick={() => open.mutate()}>
          Abrir {accountTypeLabel(type).toLowerCase()}
        </Button>
      </div>
    </Panel>
  )
}

function Opened({ account, openCount, onAnother }: { account: Account; openCount: number; onAnother: () => void }) {
  const limits = useQuery({ queryKey: ['limits', account.id], queryFn: () => bankingApi.limits(account.id) })
  const canOpenMore = openCount < MAX_OPEN_ACCOUNTS
  const next: { to: string; label: string; hint: string; icon: Icon }[] = [
    { to: `/contas/${account.id}`, label: 'Depositar', hint: 'Coloque o primeiro saldo', icon: Wallet },
    { to: '/pix/chaves', label: 'Criar chave Pix', hint: 'Receba por CPF, e-mail ou celular', icon: PixLogo },
    { to: '/transferir', label: 'Transferir', hint: 'Mande dinheiro a quem tem conta aqui', icon: ArrowsLeftRight },
  ]
  return (
    <Panel>
      <div className="flex flex-col gap-5">
        <div className="text-center">
          <CheckCircle size={44} weight="duotone" className="mx-auto text-ok" />
          <h2 className="mt-2 text-lg font-semibold">{accountTypeLabel(account.type)} aberta</h2>
          <p className="mt-1 flex items-center justify-center gap-2 text-xs text-muted">
            <Badge tone="ok">Ativa</Badge>
            <span>Aberta em {formatDateTime(account.createdAt)}</span>
          </p>
        </div>

        <dl className="flex flex-col gap-2 rounded-ui bg-bg p-4 text-sm">
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
          {limits.isPending && <Skeleton className="h-24" />}
          {limits.isError && <p className="text-sm text-muted">Não foi possível carregar os limites agora. Eles ficam em Ver conta.</p>}
          {limits.data && (
            <table className="w-full text-sm">
              <thead>
                <tr className="text-left text-xs text-muted">
                  <th scope="col" className="pb-1 font-normal">Operação</th>
                  <th scope="col" className="pb-1 text-right font-normal">Por vez</th>
                  <th scope="col" className="pb-1 text-right font-normal">Por dia</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-line">
                {limits.data.map((l) => (
                  <tr key={l.type}>
                    <th scope="row" className="py-1.5 text-left font-normal text-muted">{limitNames[l.type] ?? l.type}</th>
                    <td className="num py-1.5 text-right">{formatBRL(l.perOperation.amount)}</td>
                    <td className="num py-1.5 text-right">{formatBRL(l.daily.amount)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>

        <div>
          <h3 className="mb-2 text-sm font-medium">Próximos passos</h3>
          <ul className="grid gap-2 sm:grid-cols-3">
            {next.map((n) => (
              <li key={n.to}>
                <Link to={n.to} className="flex h-full flex-col gap-1 rounded-ui border border-line p-3 transition hover:border-muted/50 hover:bg-surface-2">
                  <n.icon size={20} className="text-accent" />
                  <span className="text-sm font-medium">{n.label}</span>
                  <span className="text-xs text-muted">{n.hint}</span>
                </Link>
              </li>
            ))}
          </ul>
        </div>

        <div className="flex flex-wrap items-center gap-3">
          <Link to={`/contas/${account.id}`}>
            <Button>Ver conta</Button>
          </Link>
          <Button variant="secondary" onClick={onAnother} disabled={!canOpenMore}>
            Abrir outra
          </Button>
          <span className="num text-xs text-muted">{openCount} de {MAX_OPEN_ACCOUNTS} contas abertas</span>
        </div>
      </div>
    </Panel>
  )
}
