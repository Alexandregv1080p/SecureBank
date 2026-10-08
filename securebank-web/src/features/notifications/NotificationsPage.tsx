import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { CaretLeft, CaretRight } from '@phosphor-icons/react'
import { bankingApi } from '../../services/banking'
import { formatDateTime } from '../../lib/format'
import { messageFor } from '../../lib/errors'
import { Button, EmptyState, ErrorState, PageHeader, Skeleton } from '../../components/ui'

export function NotificationsPage() {
  const client = useQueryClient()
  const [page, setPage] = useState(0)
  const list = useQuery({ queryKey: ['notifications', 'page', page], queryFn: () => bankingApi.notifications(page, 10), placeholderData: (p) => p })
  const read = useMutation({
    mutationFn: bankingApi.markRead,
    onSuccess: () => client.invalidateQueries({ queryKey: ['notifications'] }),
  })
  const totalPages = list.data ? Math.max(1, Math.ceil(list.data.totalElements / list.data.size)) : 1

  return (
    <>
      <PageHeader title="Avisos" description="Movimentações e acessos recentes na sua conta." />
      {list.isPending && <Skeleton className="h-48" />}
      {list.isError && <ErrorState message={messageFor(list.error)} onRetry={() => list.refetch()} />}
      {list.data?.items.length === 0 && <EmptyState title="Nenhum aviso">Quando algo acontecer na sua conta, você vê aqui.</EmptyState>}
      {!!list.data?.items.length && (
        <>
          <ul className="divide-y divide-line card overflow-hidden">
            {list.data.items.map((n) => (
              <li key={n.id} className="flex items-start justify-between gap-4 px-5 py-4">
                <div>
                  <p className={`text-sm ${n.read ? 'text-muted' : 'font-semibold'}`}>{n.title}</p>
                  <p className="mt-0.5 text-sm text-muted">{n.body}</p>
                  <p className="mt-1 text-xs text-muted">{formatDateTime(n.createdAt)}</p>
                </div>
                {!n.read && (
                  <Button variant="ghost" onClick={() => read.mutate(n.id)} disabled={read.isPending}>
                    Marcar como lido
                  </Button>
                )}
              </li>
            ))}
          </ul>
          <div className="mt-4 flex items-center justify-between text-sm text-muted">
            <span>Página {page + 1} de {totalPages}</span>
            <div className="flex gap-2">
              <Button variant="secondary" disabled={page === 0} onClick={() => setPage((p) => p - 1)} aria-label="Página anterior"><CaretLeft size={16} /></Button>
              <Button variant="secondary" disabled={page + 1 >= totalPages} onClick={() => setPage((p) => p + 1)} aria-label="Próxima página"><CaretRight size={16} /></Button>
            </div>
          </div>
        </>
      )}
    </>
  )
}
