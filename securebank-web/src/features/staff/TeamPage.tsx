import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { staffApi, type StaffUser } from '../../services/staff'
import { useAuth } from '../../stores/auth'
import { messageFor } from '../../lib/errors'
import { roleLabel } from '../../lib/staff'
import { Alert, Badge, Button, ErrorState, Field, Input, PageHeader, Panel, Select, Skeleton } from '../../components/ui'
import { ConfirmButton } from './parts'

export function TeamPage() {
  const me = useAuth((s) => s.claims?.sub)
  const client = useQueryClient()
  const users = useQuery({ queryKey: ['staff', 'users'], queryFn: staffApi.users })
  const refresh = () => client.invalidateQueries({ queryKey: ['staff', 'users'] })
  const toggle = useMutation({
    mutationFn: (u: StaffUser) => (u.status === 'ACTIVE' ? staffApi.disableUser(u.id) : staffApi.enableUser(u.id)),
    onSuccess: refresh,
  })

  return (
    <>
      <PageHeader title="Equipe" description="Usuários que acessam este painel. Desativar encerra as sessões da pessoa na hora." />
      {toggle.isError && <div className="mb-4"><Alert tone="error">{messageFor(toggle.error)}</Alert></div>}
      {users.isPending && <Skeleton className="h-40" />}
      {users.isError && <ErrorState message={messageFor(users.error)} onRetry={() => users.refetch()} />}
      {users.data && (
        <ul className="divide-y divide-line rounded-ui border border-line bg-surface">
          {users.data.map((u) => (
            <li key={u.id} className="flex flex-wrap items-center justify-between gap-3 px-5 py-4">
              <div>
                <p className="flex items-center gap-2 font-medium">
                  {u.email}
                  {u.id === me && <Badge>você</Badge>}
                  {u.status !== 'ACTIVE' && <Badge tone="warn">Desativado</Badge>}
                </p>
                <p className="text-sm text-muted">{roleLabel(u.role)}</p>
              </div>
              {u.id !== me &&
                (u.status === 'ACTIVE' ? (
                  <ConfirmButton label="Desativar" confirmLabel="Confirmar desativação" loading={toggle.isPending} onConfirm={() => toggle.mutate(u)} />
                ) : (
                  <Button variant="secondary" loading={toggle.isPending} onClick={() => toggle.mutate(u)}>Reativar</Button>
                ))}
            </li>
          ))}
        </ul>
      )}
      <div className="mt-8 max-w-md">
        <NewUser onCreated={refresh} />
      </div>
    </>
  )
}

function NewUser({ onCreated }: { onCreated: () => unknown }) {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [role, setRole] = useState<'SUPPORT' | 'ADMIN'>('SUPPORT')
  const valid = /^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email) && password.length >= 12
  const create = useMutation({
    mutationFn: () => staffApi.createUser(email.trim(), password, role),
    onSuccess: () => {
      setEmail('')
      setPassword('')
      onCreated()
    },
  })
  return (
    <Panel title="Novo usuário da equipe">
      <form className="flex flex-col gap-4" onSubmit={(e) => { e.preventDefault(); if (valid) create.mutate() }}>
        {create.isError && <Alert tone="error">{messageFor(create.error)}</Alert>}
        {create.isSuccess && <Alert tone="success">Usuário criado. Passe a senha por um canal seguro e peça a troca no primeiro acesso.</Alert>}
        <Field label="E-mail" htmlFor="staff-email">
          <Input id="staff-email" type="email" autoComplete="off" value={email} onChange={(e) => setEmail(e.target.value)} />
        </Field>
        <Field label="Senha inicial" htmlFor="staff-pass" hint="No mínimo 12 caracteres; o servidor aplica a política completa.">
          <Input id="staff-pass" type="password" autoComplete="new-password" value={password} onChange={(e) => setPassword(e.target.value)} />
        </Field>
        <Field label="Papel" htmlFor="staff-role" hint={role === 'ADMIN' ? 'Administrador altera limites, câmbio e equipe.' : 'Suporte consulta clientes e a auditoria, sem alterar nada.'}>
          <Select id="staff-role" value={role} onChange={(e) => setRole(e.target.value as 'SUPPORT' | 'ADMIN')}>
            <option value="SUPPORT">Suporte</option>
            <option value="ADMIN">Administrador</option>
          </Select>
        </Field>
        <Button type="submit" loading={create.isPending} disabled={!valid}>Criar usuário</Button>
      </form>
    </Panel>
  )
}
