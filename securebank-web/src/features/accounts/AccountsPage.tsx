import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { Link } from 'react-router'
import { bankingApi } from '../../services/banking'
import { formatBRL } from '../../lib/money'
import { accountLabel, accountTypeLabel } from '../../lib/format'
import { messageFor } from '../../lib/errors'
import { Alert, Badge, Button, EmptyState, ErrorState, PageHeader, Panel, Select, Skeleton } from '../../components/ui'
import { useAccounts, useRefreshMoney } from './hooks'

export function AccountsPage() {
  const accounts = useAccounts()
  const refresh = useRefreshMoney()
  const [type, setType] = useState<'CHECKING' | 'SAVINGS'>('CHECKING')
  const open = useMutation({ mutationFn: () => bankingApi.openAccount(type), onSuccess: refresh })

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
        <Panel title="Abrir nova conta">
          <div className="flex flex-col gap-4">
            {open.isError && <Alert tone="error">{messageFor(open.error)}</Alert>}
            {open.isSuccess && <Alert tone="success">Conta {open.data.accountNumber} aberta.</Alert>}
            <label htmlFor="type" className="text-sm font-medium">
              Tipo de conta
            </label>
            <Select id="type" value={type} onChange={(e) => setType(e.target.value as 'CHECKING' | 'SAVINGS')}>
              <option value="CHECKING">Conta corrente</option>
              <option value="SAVINGS">Poupança</option>
            </Select>
            <Button loading={open.isPending} onClick={() => open.mutate()}>
              Abrir conta
            </Button>
          </div>
        </Panel>
      </div>
    </>
  )
}
