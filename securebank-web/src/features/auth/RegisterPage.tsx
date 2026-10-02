import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { Link, Navigate, useNavigate } from 'react-router'
import { login, register } from '../../services/auth'
import { messageFor } from '../../lib/errors'
import { toE164BR } from '../../lib/phone'
import { useAuth } from '../../stores/auth'
import { Alert, Button, Field, Input } from '../../components/ui'
import { AuthLayout } from './AuthLayout'

const schema = z
  .object({
    name: z.string().trim().min(2, 'Informe o nome completo').max(120),
    document: z.string().refine((v) => v.replace(/\D/g, '').length === 11, 'O CPF tem 11 dígitos'),
    email: z.string().min(1, 'Informe o e-mail').email('Informe um e-mail válido'),
    phone: z.string().refine((v) => toE164BR(v) !== null, 'Informe DDD e número, ex.: 11 99999-8888'),
    password: z.string().min(12, 'Use ao menos 12 caracteres').max(128),
    confirm: z.string(),
  })
  .refine((v) => v.password === v.confirm, { path: ['confirm'], message: 'As senhas não conferem' })

type Values = z.infer<typeof schema>

export function RegisterPage() {
  const status = useAuth((s) => s.status)
  const navigate = useNavigate()
  const [error, setError] = useState<string | null>(null)
  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: { name: '', document: '', email: '', phone: '', password: '', confirm: '' },
  })
  const errors = form.formState.errors

  if (status === 'authenticated') return <Navigate to="/" replace />

  async function onSubmit(v: Values) {
    setError(null)
    try {
      await register({ name: v.name.trim(), document: v.document, email: v.email, phone: toE164BR(v.phone)!, password: v.password })
      await login(v.email, v.password)
      navigate('/', { replace: true })
    } catch (e) {
      setError(messageFor(e))
    }
  }

  return (
    <AuthLayout title="Abra sua conta" subtitle="Cadastro gratuito. Depois você pode ativar a verificação em duas etapas.">
      <form onSubmit={form.handleSubmit(onSubmit)} noValidate className="flex flex-col gap-5">
        {error && <Alert tone="error">{error}</Alert>}
        <Field label="Nome completo" htmlFor="name" error={errors.name?.message}>
          <Input id="name" autoComplete="name" aria-invalid={!!errors.name} {...form.register('name')} />
        </Field>
        <div className="grid gap-5 sm:grid-cols-2">
          <Field label="CPF" htmlFor="document" error={errors.document?.message}>
            <Input id="document" inputMode="numeric" placeholder="000.000.000-00" aria-invalid={!!errors.document} {...form.register('document')} />
          </Field>
          <Field label="Celular" htmlFor="phone" error={errors.phone?.message}>
            <Input id="phone" type="tel" autoComplete="tel" placeholder="11 99999-8888" aria-invalid={!!errors.phone} {...form.register('phone')} />
          </Field>
        </div>
        <Field label="E-mail" htmlFor="email" error={errors.email?.message}>
          <Input id="email" type="email" autoComplete="email" aria-invalid={!!errors.email} {...form.register('email')} />
        </Field>
        <Field label="Senha" htmlFor="password" error={errors.password?.message} hint="Mínimo de 12 caracteres. Uma frase longa vale mais que símbolos.">
          <Input id="password" type="password" autoComplete="new-password" aria-invalid={!!errors.password} {...form.register('password')} />
        </Field>
        <Field label="Confirmar senha" htmlFor="confirm" error={errors.confirm?.message}>
          <Input id="confirm" type="password" autoComplete="new-password" aria-invalid={!!errors.confirm} {...form.register('confirm')} />
        </Field>
        <Button type="submit" loading={form.formState.isSubmitting}>
          Criar conta
        </Button>
      </form>
      <p className="mt-6 text-sm text-muted">
        Já tem cadastro?{' '}
        <Link to="/entrar" className="font-medium text-accent underline-offset-4 hover:underline">
          Entrar
        </Link>
      </p>
    </AuthLayout>
  )
}
