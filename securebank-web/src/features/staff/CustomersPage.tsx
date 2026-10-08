import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link, useSearchParams } from 'react-router'
import { staffApi, type CustomerView } from '../../services/staff'
import { useAuth } from '../../stores/auth'
import { accountLabel, accountTypeLabel, formatDate } from '../../lib/format'
import { messageFor } from '../../lib/errors'
import { accountStatusLabel, isUuid, searchError } from '../../lib/staff'
import { Badge, Button, EmptyState, ErrorState, Field, Input, PageHeader, Panel, Skeleton } from '../../components/ui'
import { DataList, DataRow } from './parts'

export function CustomersPage() {
  const [params, setParams] = useSearchParams()
  const q = params.get('q') ?? ''
  const id = params.get('id') ?? ''
  const [input, setInput] = useState(q || id)
  const [page, setPage] = useState(0)
  const [touched, setTouched] = useState(false)
  const error = touched ? searchError(input) : null

  function submit() {
    setTouched(true)
    const text = input.trim()
    if (isUuid(text)) {
      setParams({ id: text }, { replace: true })
      return
    }
    if (searchError(text)) return
    setPage(0)
    setParams({ q: text }, { replace: true })
  }

  const results = useQuery({
    queryKey: ['staff', 'customers', q, page],
    queryFn: () => staffApi.searchCustomers(q, page),
    enabled: !!q && !searchError(q),
  })
  const pages = results.data ? Math.max(1, Math.ceil(results.data.totalElements / results.data.size)) : 1
  const select = (customerId: string) => setParams(q ? { q, id: customerId } : { id: customerId }, { replace: true })

  return (
    <>
      <PageHeader title="Clientes" description="Busque por nome, e-mail, telefone ou CPF completo (o CPF aparece mascarado). Cada busca fica na auditoria." />
      <form
        className="mb-6 flex max-w-xl flex-col gap-3 sm:flex-row sm:items-start"
        onSubmit={(e) => {
          e.preventDefault()
          submit()
        }}
      >
        <div className="flex-1">
          <Field label="Buscar cliente" htmlFor="customer" error={error ?? undefined} hint="Também aceita o identificador completo do cliente.">
            <Input id="customer" value={input} onChange={(e) => setInput(e.target.value)} aria-invalid={!!error} autoComplete="off" />
          </Field>
        </div>
        <Button type="submit" className="sm:mt-7">
          Buscar
        </Button>
      </form>

      {q && results.isPending && <Skeleton className="h-32" />}
      {results.isError && <ErrorState message={messageFor(results.error)} onRetry={() => results.refetch()} />}
      {results.data && results.data.items.length === 0 && <EmptyState title="Nenhum cliente encontrado">Confira a grafia, ou use o CPF completo.</EmptyState>}
      {!!results.data?.items.length && (
        <div className="mb-8">
          <ul className="divide-y divide-line card overflow-hidden">
            {results.data.items.map((c) => (
              <li key={c.id}>
                <button
                  type="button"
                  onClick={() => select(c.id)}
                  aria-current={c.id === id}
                  className={`flex w-full flex-wrap items-center justify-between gap-2 px-5 py-3 text-left hover:bg-surface-2 ${c.id === id ? 'bg-accent-soft' : ''}`}
                >
                  <span className="min-w-0">
                    <span className="block font-medium">{c.name}</span>
                    <span className="block break-all text-sm text-muted">
                      {c.document} · {c.email}
                    </span>
                  </span>
                  {c.status !== 'ACTIVE' && <Badge tone="warn">{c.status}</Badge>}
                </button>
              </li>
            ))}
          </ul>
          <div className="mt-3 flex flex-wrap items-center justify-between gap-2 text-sm text-muted">
            <span>
              {results.data.totalElements} resultado(s) · página {page + 1} de {pages}
            </span>
            <span className="flex gap-2">
              <Button variant="secondary" disabled={page === 0} onClick={() => setPage(page - 1)}>
                Anterior
              </Button>
              <Button variant="secondary" disabled={page + 1 >= pages} onClick={() => setPage(page + 1)}>
                Próxima
              </Button>
            </span>
          </div>
        </div>
      )}

      {isUuid(id) && <CustomerDetail id={id} />}
    </>
  )
}

function CustomerDetail({ id }: { id: string }) {
  const isAdmin = useAuth((s) => s.claims?.roles.includes('ADMIN'))
  const customer = useQuery({ queryKey: ['staff', 'customer', id], queryFn: () => staffApi.customer(id) })
  const accounts = useQuery({ queryKey: ['staff', 'customer-accounts', id], queryFn: () => staffApi.customerAccounts(id) })

  if (customer.isPending) return <Skeleton className="h-40" />
  if (customer.isError) return <ErrorState message={messageFor(customer.error)} onRetry={() => customer.refetch()} />
  return (
    <div className="flex flex-col gap-6">
      <CustomerCard data={customer.data} />
      <Panel title="Contas">
        {accounts.isPending && <Skeleton className="h-16" />}
        {accounts.isError && <ErrorState message={messageFor(accounts.error)} onRetry={() => accounts.refetch()} />}
        {accounts.data?.length === 0 && <p className="text-sm text-muted">Este cliente ainda não abriu contas.</p>}
        {!!accounts.data?.length && (
          <ul className="divide-y divide-line">
            {accounts.data.map((a) => (
              <li key={a.id} className="flex flex-wrap items-center justify-between gap-2 py-3 first:pt-0 last:pb-0">
                <span>
                  <span className="block font-medium">{accountTypeLabel(a.type)}</span>
                  <span className="block text-sm text-muted">{accountLabel(a)}</span>
                </span>
                <span className="flex items-center gap-3">
                  <Badge tone={a.status === 'ACTIVE' ? 'ok' : a.status === 'BLOCKED' ? 'danger' : 'warn'}>{accountStatusLabel(a.status)}</Badge>
                  {isAdmin && (
                    <Link className="text-sm font-medium text-accent hover:underline" to={`/equipe/contas?id=${a.id}`}>
                      Gerenciar
                    </Link>
                  )}
                </span>
              </li>
            ))}
          </ul>
        )}
      </Panel>
    </div>
  )
}

function CustomerCard({ data }: { data: CustomerView }) {
  return (
    <Panel title={data.name} action={<Badge tone={data.status === 'ACTIVE' ? 'ok' : 'warn'}>{data.status === 'ACTIVE' ? 'Ativo' : data.status}</Badge>}>
      <DataList>
        <DataRow label="Identificador">
          <span className="font-mono text-xs">{data.id}</span>
        </DataRow>
        <DataRow label="CPF">{data.document}</DataRow>
        <DataRow label="E-mail">{data.email}</DataRow>
        <DataRow label="Telefone">{data.phone}</DataRow>
        <DataRow label="Cliente desde">{formatDate(data.createdAt)}</DataRow>
      </DataList>
    </Panel>
  )
}
