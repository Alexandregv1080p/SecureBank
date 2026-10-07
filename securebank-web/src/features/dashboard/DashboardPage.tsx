import { useQuery } from '@tanstack/react-query'
import { Link, Navigate } from 'react-router'
import { ArrowsLeftRight, Barcode } from '@phosphor-icons/react'
import { bankingApi } from '../../services/banking'
import { useAuth } from '../../stores/auth'
import { formatBRL } from '../../lib/money'
import { formatDateTime, accountLabel, accountTypeLabel } from '../../lib/format'
import { messageFor } from '../../lib/errors'
import { Button, EmptyState, ErrorState, PageHeader, Panel, Skeleton } from '../../components/ui'
import { useAccounts, sumAmounts } from '../accounts/hooks'

export function DashboardPage() {
  const isCustomer = useAuth((s) => s.claims?.roles.includes('CUSTOMER'))
  if (!isCustomer) return <Navigate to="/equipe" replace />
  return <CustomerDashboard />
}

function CustomerDashboard() {
  const me = useQuery({ queryKey: ['me'], queryFn: bankingApi.me })
  const accounts = useAccounts()
  const notices = useQuery({ queryKey: ['notifications', 'latest'], queryFn: () => bankingApi.notifications(0, 4) })
  const firstName = me.data?.name.split(' ')[0]

  return (
    <>
      <PageHeader
        title={firstName ? `Olá, ${firstName}` : 'Olá'}
        description="Resumo das suas contas."
        action={
          <div className="flex gap-2">
            <Link to="/transferir">
              <Button>
                <ArrowsLeftRight size={18} /> Transferir
              </Button>
            </Link>
            <Link to="/pagar">
              <Button variant="secondary">
                <Barcode size={18} /> Pagar
              </Button>
            </Link>
          </div>
        }
      />

      <div className="grid gap-8 lg:grid-cols-[3fr_2fr]">
        <section>
          {accounts.isPending && <Skeleton className="h-40" />}
          {accounts.isError && <ErrorState message={messageFor(accounts.error)} onRetry={() => accounts.refetch()} />}
          {accounts.data && accounts.data.length === 0 && (
            <EmptyState
              title="Você ainda não tem uma conta"
              action={
                <Link to="/contas">
                  <Button>Abrir conta</Button>
                </Link>
              }
            >
              Abra uma conta corrente ou poupança para começar a movimentar.
            </EmptyState>
          )}
          {accounts.data && accounts.data.length > 0 && (
            <>
              <p className="text-sm text-muted">Saldo total</p>
              <p className="num mt-1 text-4xl font-semibold tracking-tight">
                {formatBRL(sumAmounts(accounts.data.map((a) => a.balance.amount)))}
              </p>
              <ul className="mt-8 divide-y divide-line rounded-ui border border-line bg-surface">
                {accounts.data.map((a) => (
                  <li key={a.id}>
                    <Link to={`/contas/${a.id}`} className="flex items-center justify-between gap-4 px-5 py-4 hover:bg-bg">
                      <div>
                        <p className="font-medium">{accountTypeLabel(a.type)}</p>
                        <p className="text-sm text-muted">{accountLabel(a)}</p>
                      </div>
                      <p className="num font-medium">{formatBRL(a.balance.amount)}</p>
                    </Link>
                  </li>
                ))}
              </ul>
            </>
          )}
        </section>

        <Panel title="Avisos recentes" action={<Link to="/notificacoes" className="text-sm font-medium text-accent hover:underline">Ver todos</Link>}>
          {notices.isPending && <Skeleton className="h-24" />}
          {notices.isError && <p className="text-sm text-muted">Não foi possível carregar os avisos.</p>}
          {notices.data && notices.data.items.length === 0 && <p className="text-sm text-muted">Nenhum aviso por enquanto.</p>}
          {notices.data && notices.data.items.length > 0 && (
            <ul className="flex flex-col gap-4">
              {notices.data.items.map((n) => (
                <li key={n.id}>
                  <p className={`text-sm ${n.read ? 'text-muted' : 'font-medium'}`}>{n.title}</p>
                  <p className="text-xs text-muted">{formatDateTime(n.createdAt)}</p>
                </li>
              ))}
            </ul>
          )}
        </Panel>
      </div>
    </>
  )
}
