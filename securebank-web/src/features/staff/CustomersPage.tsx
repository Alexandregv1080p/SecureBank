import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { staffApi } from '../../services/staff'
import { formatDate } from '../../lib/format'
import { messageFor } from '../../lib/errors'
import { isUuid } from '../../lib/staff'
import { Badge, Button, ErrorState, Field, Input, PageHeader, Panel, Skeleton } from '../../components/ui'
import { DataList, DataRow } from './parts'
import { useIdParam } from './useIdParam'

export function CustomersPage() {
  const [id, setId] = useIdParam()
  const [input, setInput] = useState(id)
  const valid = isUuid(id)
  const customer = useQuery({ queryKey: ['staff', 'customer', id], queryFn: () => staffApi.customer(id.trim()), enabled: valid })

  return (
    <>
      <PageHeader title="Clientes" description="Consulta pelo identificador do cliente. O CPF aparece mascarado." />
      <form className="mb-6 flex max-w-xl items-end gap-3" onSubmit={(e) => { e.preventDefault(); setId(input.trim()) }}>
        <div className="flex-1">
          <Field label="Identificador do cliente" htmlFor="customer" error={id && !valid ? 'Informe um identificador completo (UUID).' : undefined}>
            <Input id="customer" value={input} onChange={(e) => setInput(e.target.value)} placeholder="00000000-0000-0000-0000-000000000000" />
          </Field>
        </div>
        <Button type="submit">Buscar</Button>
      </form>

      {valid && customer.isPending && <Skeleton className="h-40" />}
      {customer.isError && <ErrorState message={messageFor(customer.error)} onRetry={() => customer.refetch()} />}
      {customer.data && (
        <Panel title={customer.data.name} action={<Badge tone={customer.data.status === 'ACTIVE' ? 'ok' : 'warn'}>{customer.data.status === 'ACTIVE' ? 'Ativo' : customer.data.status}</Badge>}>
          <DataList>
            <DataRow label="Identificador"><span className="font-mono text-xs">{customer.data.id}</span></DataRow>
            <DataRow label="CPF">{customer.data.document}</DataRow>
            <DataRow label="E-mail">{customer.data.email}</DataRow>
            <DataRow label="Telefone">{customer.data.phone}</DataRow>
            <DataRow label="Cliente desde">{formatDate(customer.data.createdAt)}</DataRow>
          </DataList>
        </Panel>
      )}
    </>
  )
}
