import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router'
import { staffApi } from '../../services/staff'
import { formatDateTime } from '../../lib/format'
import { messageFor } from '../../lib/errors'
import { auditEventLabel, auditEvents, isAlertEvent, isUuid, shortId } from '../../lib/staff'
import { Badge, Button, EmptyState, ErrorState, Field, Input, PageHeader, Select, Skeleton } from '../../components/ui'

export function AuditPage() {
  const [event, setEvent] = useState('')
  const [userInput, setUserInput] = useState('')
  const [page, setPage] = useState(0)
  const userId = isUuid(userInput) ? userInput.trim() : ''
  const userInvalid = userInput.trim() !== '' && !userId

  const logs = useQuery({
    queryKey: ['staff', 'audit', page, event, userId],
    queryFn: () => staffApi.audit(page, event || undefined, userId || undefined),
    enabled: !userInvalid,
  })
  const pages = logs.data ? Math.max(1, Math.ceil(logs.data.totalElements / logs.data.size)) : 1

  return (
    <>
      <PageHeader title="Auditoria" description="Eventos de segurança e de operações críticas, do mais recente para o mais antigo. Somente leitura." />

      <div className="mb-6 grid gap-4 sm:grid-cols-2">
        <Field label="Evento" htmlFor="event">
          <Select id="event" value={event} onChange={(e) => { setEvent(e.target.value); setPage(0) }}>
            <option value="">Todos os eventos</option>
            {Object.entries(auditEvents).map(([code, label]) => (
              <option key={code} value={code}>{label}</option>
            ))}
          </Select>
        </Field>
        <Field label="Usuário (identificador)" htmlFor="user" error={userInvalid ? 'Informe um identificador completo (UUID).' : undefined}>
          <Input id="user" value={userInput} placeholder="Identificador do usuário" aria-invalid={userInvalid} onChange={(e) => { setUserInput(e.target.value); setPage(0) }} />
        </Field>
      </div>

      {logs.isPending && !userInvalid && <Skeleton className="h-64" />}
      {logs.isError && <ErrorState message={messageFor(logs.error)} onRetry={() => logs.refetch()} />}
      {logs.data?.items.length === 0 && <EmptyState title="Nenhum evento">Nada encontrado com estes filtros.</EmptyState>}
      {!!logs.data?.items.length && (
        <>
          <ul className="divide-y divide-line rounded-ui border border-line bg-surface md:hidden">
            {logs.data.items.map((l) => (
              <li key={l.id} className="flex flex-col gap-1.5 px-4 py-3">
                <div className="flex items-start justify-between gap-3">
                  <Badge tone={isAlertEvent(l.event) ? 'warn' : 'neutral'}>{auditEventLabel(l.event)}</Badge>
                  <span className="shrink-0 text-xs text-muted">{formatDateTime(l.occurredAt)}</span>
                </div>
                <p className="text-xs text-muted">
                  Usuário <span className="font-mono">{shortId(l.userId)}</span>
                  {l.accountId && (
                    <>
                      {' · '}Conta{' '}
                      <Link className="font-mono text-accent hover:underline" to={`/equipe/contas?id=${l.accountId}`}>
                        {shortId(l.accountId)}
                      </Link>
                    </>
                  )}
                  {l.ip && <> · {l.ip}</>}
                </p>
                {l.detail && <p className="break-all text-xs">{l.detail}</p>}
              </li>
            ))}
          </ul>
          <div className="hidden overflow-x-auto rounded-ui border border-line bg-surface md:block">
            <table className="w-full min-w-[44rem] text-left text-sm">
              <thead className="border-b border-line text-xs text-muted">
                <tr>
                  <th className="px-4 py-3 font-medium">Quando</th>
                  <th className="px-4 py-3 font-medium">Evento</th>
                  <th className="px-4 py-3 font-medium">Usuário</th>
                  <th className="px-4 py-3 font-medium">Conta</th>
                  <th className="px-4 py-3 font-medium">IP</th>
                  <th className="px-4 py-3 font-medium">Detalhe</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-line">
                {logs.data.items.map((l) => (
                  <tr key={l.id} className="align-top">
                    <td className="whitespace-nowrap px-4 py-3 text-muted">{formatDateTime(l.occurredAt)}</td>
                    <td className="px-4 py-3">
                      <Badge tone={isAlertEvent(l.event) ? 'warn' : 'neutral'}>{auditEventLabel(l.event)}</Badge>
                    </td>
                    <td className="px-4 py-3 font-mono text-xs" title={l.userId ?? undefined}>{shortId(l.userId)}</td>
                    <td className="px-4 py-3 font-mono text-xs">
                      {l.accountId ? <Link className="text-accent hover:underline" title={l.accountId} to={`/equipe/contas?id=${l.accountId}`}>{shortId(l.accountId)}</Link> : '—'}
                    </td>
                    <td className="px-4 py-3 font-mono text-xs">{l.ip ?? '—'}</td>
                    <td className="max-w-[18rem] break-words px-4 py-3 text-xs text-muted">{l.detail ?? '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="mt-4 flex items-center justify-between text-sm text-muted">
            <span>{logs.data.totalElements} eventos · página {page + 1} de {pages}</span>
            <span className="flex gap-2">
              <Button variant="secondary" disabled={page === 0} onClick={() => setPage(page - 1)}>Anterior</Button>
              <Button variant="secondary" disabled={page + 1 >= pages} onClick={() => setPage(page + 1)}>Próxima</Button>
            </span>
          </div>
        </>
      )}
    </>
  )
}
