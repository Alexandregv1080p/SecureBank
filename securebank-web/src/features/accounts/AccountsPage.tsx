import { Link } from 'react-router'
import { formatBRL } from '../../lib/money'
import { accountLabel, accountTypeLabel } from '../../lib/format'
import { messageFor } from '../../lib/errors'
import { Badge, EmptyState, ErrorState, PageHeader, Skeleton } from '../../components/ui'
import { useAccounts } from './hooks'
import { OpenAccount } from './OpenAccount'

export function AccountsPage() {
  const accounts = useAccounts()

  return (
    <>
      <PageHeader title="Contas" description="Suas contas e saldos. Abra uma nova quando precisar." />

      {accounts.isPending && <Skeleton className="h-32" />}
      {accounts.isError && <ErrorState message={messageFor(accounts.error)} onRetry={() => accounts.refetch()} />}
      {accounts.data?.length === 0 && <EmptyState title="Nenhuma conta aberta">Use o formulário abaixo para abrir a primeira.</EmptyState>}
      {!!accounts.data?.length && (
        <ul className="divide-y divide-line card overflow-hidden">
          {accounts.data.map((a) => (
            <li key={a.id}>
              <Link to={`/contas/${a.id}`} className="flex flex-wrap items-center justify-between gap-3 px-5 py-4 hover:bg-surface-2">
                <div>
                  <p className="flex items-center gap-2 font-medium">
                    {accountTypeLabel(a.type)}
                    {a.status !== 'ACTIVE' && <Badge tone="warn">{a.status === 'BLOCKED' ? 'Bloqueada' : 'Encerrada'}</Badge>}
                  </p>
                  <p className="text-sm text-muted">{accountLabel(a)}</p>
                </div>
                <p className="num text-lg font-medium">{formatBRL(a.balance.amount)}</p>
              </Link>
            </li>
          ))}
        </ul>
      )}

      <div className="mt-8 max-w-md">
        <OpenAccount />
      </div>
    </>
  )
}
