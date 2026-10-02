import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { Link, Navigate, useLocation, useNavigate } from 'react-router'
import { login, verifyMfa } from '../../services/auth'
import { messageFor } from '../../lib/errors'
import { useAuth } from '../../stores/auth'
import { Alert, Button, Field, Input } from '../../components/ui'
import { AuthLayout } from './AuthLayout'

const credentials = z.object({
  email: z.string().min(1, 'Informe o e-mail').email('Informe um e-mail válido'),
  password: z.string().min(1, 'Informe a senha'),
})
const second = z.object({ code: z.string().regex(/^\d{6}$/, 'Digite os 6 dígitos do aplicativo') })

export function LoginPage() {
  const status = useAuth((s) => s.status)
  const navigate = useNavigate()
  const location = useLocation()
  const [mfaToken, setMfaToken] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const from = (location.state as { from?: string } | null)?.from ?? '/'

  const first = useForm({ resolver: zodResolver(credentials), defaultValues: { email: '', password: '' } })
  const mfa = useForm({ resolver: zodResolver(second), defaultValues: { code: '' } })

  if (status === 'authenticated') return <Navigate to={from} replace />

  async function submitCredentials(values: z.infer<typeof credentials>) {
    setError(null)
    try {
      const outcome = await login(values.email, values.password)
      if (outcome.kind === 'mfa') setMfaToken(outcome.mfaToken)
      else navigate(from, { replace: true })
    } catch (e) {
      setError(messageFor(e))
    }
  }

  async function submitCode(values: z.infer<typeof second>) {
    setError(null)
    try {
      await verifyMfa(mfaToken!, values.code)
      navigate(from, { replace: true })
    } catch (e) {
      setError(messageFor(e))
      mfa.reset()
    }
  }

  if (mfaToken) {
    return (
      <AuthLayout title="Verificação em duas etapas" subtitle="Digite o código de 6 dígitos do seu aplicativo autenticador.">
        <form onSubmit={mfa.handleSubmit(submitCode)} noValidate className="flex flex-col gap-5">
          {error && <Alert tone="error">{error}</Alert>}
          <Field label="Código" htmlFor="code" error={mfa.formState.errors.code?.message}>
            <Input
              id="code"
              inputMode="numeric"
              autoComplete="one-time-code"
              maxLength={6}
              autoFocus
              className="num text-center text-lg tracking-[0.4em]"
              aria-invalid={!!mfa.formState.errors.code}
              {...mfa.register('code')}
            />
          </Field>
          <Button type="submit" loading={mfa.formState.isSubmitting}>
            Confirmar
          </Button>
          <Button
            type="button"
            variant="ghost"
            onClick={() => {
              setMfaToken(null)
              setError(null)
            }}
          >
            Voltar
          </Button>
        </form>
      </AuthLayout>
    )
  }

  return (
    <AuthLayout title="Acesse sua conta" subtitle="Entre com o e-mail e a senha do seu cadastro.">
      <form onSubmit={first.handleSubmit(submitCredentials)} noValidate className="flex flex-col gap-5">
        {error && <Alert tone="error">{error}</Alert>}
        <Field label="E-mail" htmlFor="email" error={first.formState.errors.email?.message}>
          <Input id="email" type="email" autoComplete="username" autoFocus aria-invalid={!!first.formState.errors.email} {...first.register('email')} />
        </Field>
        <Field label="Senha" htmlFor="password" error={first.formState.errors.password?.message}>
          <Input id="password" type="password" autoComplete="current-password" aria-invalid={!!first.formState.errors.password} {...first.register('password')} />
        </Field>
        <Button type="submit" loading={first.formState.isSubmitting}>
          Entrar
        </Button>
      </form>
      <p className="mt-6 text-sm text-muted">
        Ainda não tem conta?{' '}
        <Link to="/cadastro" className="font-medium text-accent underline-offset-4 hover:underline">
          Abrir cadastro
        </Link>
      </p>
    </AuthLayout>
  )
}
