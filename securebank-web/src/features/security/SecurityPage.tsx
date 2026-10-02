import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import QRCode from 'qrcode'
import { securityApi } from '../../services/security'
import { formatDateTime } from '../../lib/format'
import { messageFor } from '../../lib/errors'
import { Alert, Badge, Button, ErrorState, Field, Input, PageHeader, Panel, Skeleton } from '../../components/ui'

export function SecurityPage() {
  return (
    <>
      <PageHeader title="Segurança" description="Controle como você acessa a sua conta." />
      <div className="grid max-w-3xl gap-8">
        <MfaPanel />
        <PasswordPanel />
        <SessionsPanel />
      </div>
    </>
  )
}

const code = z.object({ code: z.string().regex(/^\d{6}$/, 'Digite os 6 dígitos do aplicativo') })

function MfaPanel() {
  const client = useQueryClient()
  const status = useQuery({ queryKey: ['mfa'], queryFn: securityApi.mfaStatus })
  const [setup, setSetup] = useState<{ secret: string; otpauthUri: string } | null>(null)
  const [error, setError] = useState<string | null>(null)
  const form = useForm({ resolver: zodResolver(code), defaultValues: { code: '' } })

  const qr = useQuery({
    queryKey: ['mfa-qr', setup?.otpauthUri],
    queryFn: () => QRCode.toDataURL(setup!.otpauthUri, { margin: 1, width: 192 }),
    enabled: !!setup,
    gcTime: 0, // o segredo não fica em cache depois de usado
  })

  const start = useMutation({ mutationFn: securityApi.setupMfa, onSuccess: setSetup, onError: (e) => setError(messageFor(e)) })
  const finish = async ({ code: value }: z.infer<typeof code>) => {
    setError(null)
    try {
      await (setup ? securityApi.confirmMfa(value) : securityApi.disableMfa(value))
      setSetup(null)
      form.reset()
      await client.invalidateQueries({ queryKey: ['mfa'] })
    } catch (e) {
      setError(messageFor(e))
      form.reset()
    }
  }

  const enabled = status.data?.enabled

  return (
    <Panel title="Verificação em duas etapas" action={enabled !== undefined && <Badge tone={enabled ? 'ok' : 'warn'}>{enabled ? 'Ativa' : 'Desativada'}</Badge>}>
      {status.isPending && <Skeleton className="h-20" />}
      {status.isError && <ErrorState message={messageFor(status.error)} onRetry={() => status.refetch()} />}
      {error && <div className="mb-4"><Alert tone="error">{error}</Alert></div>}

      {enabled === false && !setup && (
        <div className="flex flex-col items-start gap-4">
          <p className="text-sm text-muted">Peça um código do seu aplicativo autenticador a cada login. Protege a conta mesmo que a senha vaze.</p>
          <Button loading={start.isPending} onClick={() => { setError(null); start.mutate() }}>Ativar</Button>
        </div>
      )}

      {setup && (
        <form onSubmit={form.handleSubmit(finish)} noValidate className="flex flex-col gap-5">
          <p className="text-sm text-muted">1. Leia o QR code no aplicativo autenticador. 2. Digite o código de 6 dígitos que ele mostrar.</p>
          <div className="flex flex-wrap items-center gap-6">
            {qr.data ? <img src={qr.data} alt="QR code para o aplicativo autenticador" width={192} height={192} className="rounded-ui border border-line bg-white p-1" /> : <Skeleton className="size-48" />}
            <div className="text-sm">
              <p className="text-muted">Sem câmera? Digite a chave:</p>
              <p className="num mt-1 break-all font-medium">{setup.secret}</p>
            </div>
          </div>
          <Field label="Código" htmlFor="mfa-code" error={form.formState.errors.code?.message}>
            <Input id="mfa-code" inputMode="numeric" autoComplete="one-time-code" maxLength={6} className="num w-40 text-center tracking-[0.3em]" aria-invalid={!!form.formState.errors.code} {...form.register('code')} />
          </Field>
          <div className="flex gap-3">
            <Button type="submit" loading={form.formState.isSubmitting}>Confirmar e ativar</Button>
            <Button type="button" variant="secondary" onClick={() => { setSetup(null); setError(null) }}>Cancelar</Button>
          </div>
        </form>
      )}

      {enabled && !setup && (
        <form onSubmit={form.handleSubmit(finish)} noValidate className="flex flex-col items-start gap-4">
          <p className="text-sm text-muted">Para desativar, confirme com um código válido do aplicativo.</p>
          <Field label="Código" htmlFor="mfa-off" error={form.formState.errors.code?.message}>
            <Input id="mfa-off" inputMode="numeric" autoComplete="one-time-code" maxLength={6} className="num w-40 text-center tracking-[0.3em]" aria-invalid={!!form.formState.errors.code} {...form.register('code')} />
          </Field>
          <Button type="submit" variant="danger" loading={form.formState.isSubmitting}>Desativar</Button>
        </form>
      )}
    </Panel>
  )
}

const passwordSchema = z
  .object({
    current: z.string().min(1, 'Informe a senha atual'),
    next: z.string().min(12, 'Use ao menos 12 caracteres').max(128),
    confirm: z.string(),
  })
  .refine((v) => v.next === v.confirm, { path: ['confirm'], message: 'As senhas não conferem' })

function PasswordPanel() {
  const client = useQueryClient()
  const [error, setError] = useState<string | null>(null)
  const [done, setDone] = useState(false)
  const form = useForm({ resolver: zodResolver(passwordSchema), defaultValues: { current: '', next: '', confirm: '' } })
  const errors = form.formState.errors

  async function onSubmit(v: z.infer<typeof passwordSchema>) {
    setError(null)
    setDone(false)
    try {
      await securityApi.changePassword(v.current, v.next)
      form.reset()
      setDone(true)
      await client.invalidateQueries({ queryKey: ['sessions'] })
    } catch (e) {
      setError(messageFor(e))
    }
  }

  return (
    <Panel title="Trocar senha">
      <form onSubmit={form.handleSubmit(onSubmit)} noValidate className="flex max-w-md flex-col gap-5">
        {error && <Alert tone="error">{error}</Alert>}
        {done && <Alert tone="success">Senha alterada. Os outros dispositivos foram desconectados.</Alert>}
        <Field label="Senha atual" htmlFor="pw-current" error={errors.current?.message}>
          <Input id="pw-current" type="password" autoComplete="current-password" aria-invalid={!!errors.current} {...form.register('current')} />
        </Field>
        <Field label="Nova senha" htmlFor="pw-next" error={errors.next?.message} hint="Mínimo de 12 caracteres.">
          <Input id="pw-next" type="password" autoComplete="new-password" aria-invalid={!!errors.next} {...form.register('next')} />
        </Field>
        <Field label="Confirmar nova senha" htmlFor="pw-confirm" error={errors.confirm?.message}>
          <Input id="pw-confirm" type="password" autoComplete="new-password" aria-invalid={!!errors.confirm} {...form.register('confirm')} />
        </Field>
        <Button type="submit" loading={form.formState.isSubmitting} className="self-start">Alterar senha</Button>
      </form>
    </Panel>
  )
}

function SessionsPanel() {
  const client = useQueryClient()
  const sessions = useQuery({ queryKey: ['sessions'], queryFn: securityApi.sessions })
  const revoke = useMutation({
    mutationFn: securityApi.revokeSession,
    onSuccess: () => client.invalidateQueries({ queryKey: ['sessions'] }),
  })

  return (
    <Panel title="Dispositivos conectados">
      {sessions.isPending && <Skeleton className="h-24" />}
      {sessions.isError && <ErrorState message={messageFor(sessions.error)} onRetry={() => sessions.refetch()} />}
      {revoke.isError && <div className="mb-4"><Alert tone="error">{messageFor(revoke.error)}</Alert></div>}
      {sessions.data && (
        <ul className="divide-y divide-line">
          {sessions.data.map((s) => (
            <li key={s.id} className="flex flex-wrap items-center justify-between gap-3 py-4 first:pt-0 last:pb-0">
              <div className="min-w-0">
                <p className="flex items-center gap-2 text-sm font-medium">
                  <span className="truncate">{s.userAgent ?? 'Dispositivo desconhecido'}</span>
                  {s.current && <Badge tone="ok">Este dispositivo</Badge>}
                  {s.mfaVerified && <Badge>2 etapas</Badge>}
                </p>
                <p className="text-xs text-muted">
                  IP {s.ip ?? 'desconhecido'}, iniciada em {formatDateTime(s.createdAt)}
                </p>
              </div>
              {!s.current && (
                <Button variant="secondary" loading={revoke.isPending && revoke.variables === s.id} onClick={() => revoke.mutate(s.id)}>
                  Encerrar
                </Button>
              )}
            </li>
          ))}
        </ul>
      )}
    </Panel>
  )
}
