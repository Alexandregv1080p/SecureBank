import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { UsersThree } from '@phosphor-icons/react'
import { staffApi, type StaffUser } from '../../services/staff'
import { useAuth } from '../../stores/auth'
import { messageFor } from '../../lib/errors'
import { departmentLabel, departments, levelLabel, type Department, type StaffLevel } from '../../lib/staff'
import { Alert, Badge, Button, ErrorState, Field, Input, PageHeader, Panel, Select, Skeleton } from '../../components/ui'
import { ConfirmButton } from '../../components/ConfirmButton'
import { Dialog } from '../../components/Dialog'

const byName = (a: StaffUser, b: StaffUser) => Number(b.status === 'ACTIVE') - Number(a.status === 'ACTIVE') || a.email.localeCompare(b.email)

/** Equipe organizada em departamentos, cada um com seus gerentes e analistas. O papel (permissões) vem do departamento. */
export function TeamPage() {
  const me = useAuth((s) => s.claims?.sub)
  const client = useQueryClient()
  const [moving, setMoving] = useState<StaffUser | null>(null)
  const users = useQuery({ queryKey: ['staff', 'users'], queryFn: staffApi.users })
  const refresh = () => client.invalidateQueries({ queryKey: ['staff', 'users'] })
  const toggle = useMutation({
    mutationFn: (u: StaffUser) => (u.status === 'ACTIVE' ? staffApi.disableUser(u.id) : staffApi.enableUser(u.id)),
    onSuccess: refresh,
  })

  const active = users.data?.filter((u) => u.status === 'ACTIVE') ?? []
  const withoutMfa = active.filter((u) => !u.mfaEnabled).length

  return (
    <>
      <PageHeader
        title="Equipe"
        description="Departamentos, níveis e acesso de cada pessoa. Mudar a posição ou desativar encerra as sessões da pessoa na hora."
      />
      {toggle.isError && <div className="mb-4"><Alert tone="error">{messageFor(toggle.error)}</Alert></div>}
      {users.isPending && <Skeleton className="h-40" />}
      {users.isError && <ErrorState message={messageFor(users.error)} onRetry={() => users.refetch()} />}
      {users.data && (
        <>
          <dl className="mb-6 grid gap-4 sm:grid-cols-3">
            <Stat label="Pessoas ativas" value={active.length} />
            <Stat label="Gerentes" value={active.filter((u) => u.level === 'MANAGER').length} />
            <Stat label="Sem verificação em duas etapas" value={withoutMfa} warn={withoutMfa > 0} />
          </dl>
          <div className="grid gap-6 xl:grid-cols-2">
            {departments.map((d) => {
              const members = users.data.filter((u) => u.department === d.code)
              const managers = members.filter((u) => u.level === 'MANAGER').sort(byName)
              const analysts = members.filter((u) => u.level === 'ANALYST').sort(byName)
              return (
                <Panel key={d.code} title={d.label} action={<Badge>{members.filter((u) => u.status === 'ACTIVE').length} ativos</Badge>}>
                  <p className="text-sm">{d.summary}</p>
                  <p className="mt-1 text-xs text-muted">{d.hierarchy}</p>
                  <div className="mt-4 flex flex-col gap-4">
                    <Group title="Gerentes" members={managers} me={me} toggle={toggle} onMove={setMoving} />
                    {d.code !== 'DIRETORIA' && <Group title="Analistas" members={analysts} me={me} toggle={toggle} onMove={setMoving} />}
                  </div>
                </Panel>
              )
            })}
          </div>
        </>
      )}
      <div className="mt-8 max-w-xl">
        <NewUser onCreated={refresh} />
      </div>

      <Dialog open={moving !== null} onClose={() => setMoving(null)} title="Mudar posição" icon={<UsersThree size={22} weight="duotone" />}>
        {moving && <MoveBody key={moving.id} user={moving} onClose={() => setMoving(null)} onDone={refresh} />}
      </Dialog>
    </>
  )
}

function Stat({ label, value, warn }: { label: string; value: number; warn?: boolean }) {
  return (
    <div className="card p-5">
      <dt className="text-sm text-muted">{label}</dt>
      <dd className={`num mt-1 text-3xl font-semibold ${warn ? 'text-warn' : ''}`}>{value}</dd>
    </div>
  )
}

function Group({ title, members, me, toggle, onMove }: {
  title: string
  members: StaffUser[]
  me: string | undefined
  toggle: { mutate: (u: StaffUser) => void; isPending: boolean }
  onMove: (u: StaffUser) => void
}) {
  return (
    <section aria-label={title}>
      <h3 className="mb-1 text-xs font-medium uppercase tracking-wider text-muted">{title}</h3>
      {members.length === 0 && <p className="py-2 text-sm text-muted">Ninguém aqui ainda.</p>}
      <ul className="divide-y divide-line">
        {members.map((u) => (
          <li key={u.id} className="flex flex-wrap items-center justify-between gap-3 py-3">
            <div className="min-w-0">
              <p className="flex flex-wrap items-center gap-2 break-all text-sm font-medium">
                {u.email}
                {u.id === me && <Badge>você</Badge>}
                {u.status !== 'ACTIVE' && <Badge tone="warn">Desativado</Badge>}
                {u.status === 'ACTIVE' && !u.mfaEnabled && <Badge tone="warn">Sem 2 etapas</Badge>}
              </p>
            </div>
            {u.id !== me && (
              <span className="ml-auto flex shrink-0 flex-wrap items-center gap-2">
                {u.status === 'ACTIVE' && <Button variant="secondary" onClick={() => onMove(u)}>Mudar posição</Button>}
                {u.status === 'ACTIVE' ? (
                  <ConfirmButton label="Desativar" confirmLabel="Confirmar desativação" loading={toggle.isPending} onConfirm={() => toggle.mutate(u)} />
                ) : (
                  <Button variant="secondary" loading={toggle.isPending} onClick={() => toggle.mutate(u)}>Reativar</Button>
                )}
              </span>
            )}
          </li>
        ))}
      </ul>
    </section>
  )
}

function PositionFields({ department, level, onDepartment, onLevel, idPrefix }: {
  department: Department
  level: StaffLevel
  onDepartment: (d: Department) => void
  onLevel: (l: StaffLevel) => void
  idPrefix: string
}) {
  const info = departments.find((d) => d.code === department)!
  const board = department === 'DIRETORIA'
  return (
    <>
      <Field label="Departamento" htmlFor={`${idPrefix}-dept`} hint={info.summary}>
        <Select id={`${idPrefix}-dept`} value={department} onChange={(e) => onDepartment(e.target.value as Department)}>
          {departments.map((d) => <option key={d.code} value={d.code}>{d.label}</option>)}
        </Select>
      </Field>
      <Field label="Nível" htmlFor={`${idPrefix}-level`} hint={board ? 'A Diretoria só tem gerentes.' : info.hierarchy}>
        <Select id={`${idPrefix}-level`} value={board ? 'MANAGER' : level} disabled={board} onChange={(e) => onLevel(e.target.value as StaffLevel)}>
          <option value="ANALYST">{levelLabel('ANALYST')}</option>
          <option value="MANAGER">{levelLabel('MANAGER')}</option>
        </Select>
      </Field>
    </>
  )
}

function MoveBody({ user, onClose, onDone }: { user: StaffUser; onClose: () => void; onDone: () => unknown }) {
  const [department, setDepartment] = useState<Department>(user.department)
  const [level, setLevel] = useState<StaffLevel>(user.level)
  const effectiveLevel = department === 'DIRETORIA' ? 'MANAGER' : level
  const unchanged = department === user.department && effectiveLevel === user.level
  const move = useMutation({
    mutationFn: () => staffApi.assignUser(user.id, department, effectiveLevel),
    onSuccess: async () => {
      await onDone()
      onClose()
    },
  })
  return (
    <form className="flex flex-col gap-4" onSubmit={(e) => { e.preventDefault(); if (!unchanged) move.mutate() }}>
      <p className="break-all text-sm text-muted">
        {user.email} é {levelLabel(user.level).toLowerCase()} de {departmentLabel(user.department)}. As permissões acompanham o departamento, e as sessões abertas da pessoa caem para valer a mudança.
      </p>
      {move.isError && <Alert tone="error">{messageFor(move.error)}</Alert>}
      <PositionFields idPrefix="move" department={department} level={level} onDepartment={setDepartment} onLevel={setLevel} />
      <div className="-mx-6 -mb-4 flex flex-wrap justify-end gap-3 border-t border-line bg-surface-2/60 px-6 py-4">
        <Button type="button" variant="secondary" onClick={onClose} disabled={move.isPending}>Voltar</Button>
        <Button type="submit" loading={move.isPending} disabled={unchanged}>Mudar posição</Button>
      </div>
    </form>
  )
}

function NewUser({ onCreated }: { onCreated: () => unknown }) {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [department, setDepartment] = useState<Department>('ATENDIMENTO')
  const [level, setLevel] = useState<StaffLevel>('ANALYST')
  const effectiveLevel = department === 'DIRETORIA' ? 'MANAGER' : level
  const valid = /^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email) && password.length >= 12
  const create = useMutation({
    mutationFn: () => staffApi.createUser(email.trim(), password, department, effectiveLevel),
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
        {create.isSuccess && <Alert tone="success">Usuário criado. Passe a senha por um canal seguro: no primeiro acesso a pessoa liga a verificação em duas etapas e troca a senha em Minha segurança.</Alert>}
        <Field label="E-mail" htmlFor="staff-email">
          <Input id="staff-email" type="email" autoComplete="off" value={email} onChange={(e) => setEmail(e.target.value)} />
        </Field>
        <Field label="Senha inicial" htmlFor="staff-pass" hint="No mínimo 12 caracteres; o servidor aplica a política completa.">
          <Input id="staff-pass" type="password" autoComplete="new-password" value={password} onChange={(e) => setPassword(e.target.value)} />
        </Field>
        <PositionFields idPrefix="staff" department={department} level={level} onDepartment={setDepartment} onLevel={setLevel} />
        <Button type="submit" loading={create.isPending} disabled={!valid}>Criar usuário</Button>
      </form>
    </Panel>
  )
}
