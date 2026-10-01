import { useQuery } from '@tanstack/react-query'
import { fetchHealth, type HealthStatus } from '../../services/health'

const label: Record<HealthStatus | 'UNREACHABLE', { text: string; dot: string }> = {
  UP: { text: 'Operacional', dot: 'bg-emerald-500' },
  DOWN: { text: 'Indisponível', dot: 'bg-red-500' },
  OUT_OF_SERVICE: { text: 'Fora de serviço', dot: 'bg-amber-500' },
  UNKNOWN: { text: 'Desconhecido', dot: 'bg-slate-400' },
  UNREACHABLE: { text: 'Sem conexão com a API', dot: 'bg-red-500' },
}

export function ApiStatusCard() {
  const { data, isPending, isError } = useQuery({
    queryKey: ['health'],
    queryFn: ({ signal }) => fetchHealth(signal),
    refetchInterval: 15_000,
    retry: false,
  })

  const state = isError ? 'UNREACHABLE' : data
  const view = state ? label[state] : null

  return (
    <section className="rounded-xl border border-slate-200 bg-white p-6 shadow-sm dark:border-slate-800 dark:bg-slate-900">
      <h2 className="text-sm font-medium text-slate-500 dark:text-slate-400">Core Banking API</h2>
      <p className="mt-3 flex items-center gap-2 text-lg font-semibold" aria-live="polite">
        <span className={`size-2.5 rounded-full ${view?.dot ?? 'bg-slate-300 animate-pulse'}`} aria-hidden />
        {isPending ? 'Verificando…' : view?.text}
      </p>
    </section>
  )
}
