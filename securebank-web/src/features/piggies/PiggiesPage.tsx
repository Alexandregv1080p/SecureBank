import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router'
import { PiggyBank } from '@phosphor-icons/react'
import { piggyApi } from '../../services/piggies'
import { messageFor } from '../../lib/errors'
import { formatBRL } from '../../lib/money'
import { MAX_ACTIVE, progress } from '../../lib/piggy'
import { sumAmounts } from '../accounts/hooks'
import { Badge, Button, EmptyState, ErrorState, PageHeader, Skeleton } from '../../components/ui'

export function PiggiesPage() {
  const piggies = useQuery({ queryKey: ['piggies'], queryFn: piggyApi.list })
  const list = piggies.data ?? []
  const total = sumAmounts(list.map((p) => p.balance.amount))
  const full = list.length >= MAX_ACTIVE

  return (
    <>
      <PageHeader
        title="Porquinhos"
        description="Reservas com nome e meta, separadas do saldo da conta. Guarde e resgate quando quiser."
        action={full ? <Badge tone="warn">Limite de {MAX_ACTIVE} porquinhos</Badge> : <Link to="/porquinhos/novo"><Button>Novo porquinho</Button></Link>}
      />

      {piggies.isPending && <Skeleton className="h-40" />}
      {piggies.isError && <ErrorState message={messageFor(piggies.error)} onRetry={() => piggies.refetch()} />}
      {piggies.data?.length === 0 && (
        <EmptyState title="Você ainda não tem porquinhos" action={<Link to="/porquinhos/novo"><Button>Criar o primeiro</Button></Link>}>
          Crie um para a viagem, a reserva de emergência ou qualquer meta. O dinheiro sai do saldo da conta e fica guardado.
        </EmptyState>
      )}

      {list.length > 0 && (
        <>
          <p className="mb-6 text-sm text-muted">
            Total guardado <span className="num ml-1 text-xl font-semibold text-ink">{formatBRL(total)}</span>
          </p>
          <ul className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
            {list.map((p, i) => {
              const pct = p.progressPercent ?? progress(p.balance.amount, p.goal?.amount ?? null)
              return (
                <li key={p.id} className="rise" style={{ '--i': i } as React.CSSProperties}>
                  <Link to={`/porquinhos/${p.id}`} className="card block h-full p-5 transition hover:border-accent/50">
                    <div className="flex items-start justify-between gap-3">
                      <span className="grid size-10 place-items-center rounded-xl bg-accent-soft text-accent"><PiggyBank size={20} /></span>
                      {p.goalReached && <Badge tone="ok">Meta alcançada</Badge>}
                    </div>
                    <p className="mt-4 font-medium">{p.name}</p>
                    <p className="num mt-1 text-2xl font-semibold tracking-tight">{formatBRL(p.balance.amount)}</p>
                    {p.goal && pct !== null ? (
                      <>
                        <div className="mt-4 h-2 overflow-hidden rounded-full bg-surface-2" role="progressbar" aria-valuenow={pct} aria-valuemin={0} aria-valuemax={100} aria-label={`Progresso de ${p.name}`}>
                          <div className="h-full rounded-full bg-accent transition-all" style={{ width: `${pct}%` }} />
                        </div>
                        <p className="mt-2 text-xs text-muted">{pct}% de {formatBRL(p.goal.amount)}</p>
                      </>
                    ) : (
                      <p className="mt-4 text-xs text-muted">Sem meta</p>
                    )}
                  </Link>
                </li>
              )
            })}
          </ul>
        </>
      )}
    </>
  )
}
