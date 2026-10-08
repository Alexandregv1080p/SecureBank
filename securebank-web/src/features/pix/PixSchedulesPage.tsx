import { useMutation, useQuery } from '@tanstack/react-query'
import { Link } from 'react-router'
import { pixApi } from '../../services/pix'
import { messageFor } from '../../lib/errors'
import { formatBRL } from '../../lib/money'
import { displayDay, scheduleFailure, scheduleStatusLabel } from '../../lib/pix'
import { Alert, Badge, Button, EmptyState, ErrorState, PageHeader, Skeleton } from '../../components/ui'
import { ConfirmButton } from '../../components/ConfirmButton'
import { useRefreshPix } from './hooks'

const tones = { SCHEDULED: 'warn', EXECUTED: 'ok', FAILED: 'danger', CANCELED: 'neutral' } as const

/** Pix agendados: lista (com o motivo quando não foi realizado) e cancelamento dos ainda pendentes. */
export function PixSchedulesPage() {
  const schedules = useQuery({ queryKey: ['pix', 'schedules'], queryFn: () => pixApi.schedules() })
  const refresh = useRefreshPix()
  const cancel = useMutation({ mutationFn: (id: string) => pixApi.cancelSchedule(id), onSuccess: () => refresh() })

  return (
    <>
      <PageHeader title="Pix agendados" description="A autorização é dada ao agendar; o dinheiro só sai na data. Você pode cancelar até lá." action={<Link to="/pix/enviar"><Button>Agendar um Pix</Button></Link>} />
      {schedules.isPending && <Skeleton className="h-40" />}
      {schedules.isError && <ErrorState message={messageFor(schedules.error)} onRetry={() => schedules.refetch()} />}
      {schedules.data?.items.length === 0 && <EmptyState title="Nenhum Pix agendado">Ao enviar um Pix, ligue &quot;Agendar para outra data&quot;.</EmptyState>}
      {cancel.isError && <div className="mb-3"><Alert tone="error">{messageFor(cancel.error)}</Alert></div>}
      {!!schedules.data?.items.length && (
        <ul className="card divide-y divide-line overflow-hidden">
          {schedules.data.items.map((s) => (
            <li key={s.id} className="flex flex-wrap items-center justify-between gap-3 px-5 py-4">
              <div className="min-w-0">
                <p className="flex flex-wrap items-center gap-2 text-sm font-medium">
                  Para {s.destinationName} <Badge tone={tones[s.status]}>{scheduleStatusLabel(s.status)}</Badge>
                </p>
                <p className={`text-xs ${s.status === 'FAILED' ? 'text-danger' : 'text-muted'}`}>
                  {displayDay(s.scheduledFor)}
                  {s.status === 'FAILED' && ` · ${scheduleFailure(s.failureReason)}`}
                  {s.message && ` · ${s.message}`}
                </p>
              </div>
              <div className="flex items-center gap-3">
                <span className="num font-medium">{formatBRL(s.amount.amount)}</span>
                {s.status === 'SCHEDULED' && <ConfirmButton label="Cancelar" confirmLabel="Confirmar cancelamento" loading={cancel.isPending} onConfirm={() => cancel.mutate(s.id)} />}
              </div>
            </li>
          ))}
        </ul>
      )}
    </>
  )
}
