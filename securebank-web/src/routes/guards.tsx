import { Link, Navigate, Outlet, useLocation } from 'react-router'
import { AppShell } from '../components/AppShell'
import { Skeleton } from '../components/ui'
import { canAccess, isStaff } from '../lib/staff'
import { useAuth } from '../stores/auth'

/** Só deixa passar quem tem sessão. A sessão é recuperada pelo cookie HttpOnly na abertura do app. */
export function RequireAuth() {
  const status = useAuth((s) => s.status)
  const location = useLocation()
  if (status === 'loading') {
    return (
      <div className="mx-auto max-w-5xl p-10">
        <Skeleton className="h-10 w-64" />
      </div>
    )
  }
  if (status === 'anonymous') return <Navigate to="/entrar" replace state={{ from: location.pathname }} />
  return <AppShell />
}

/** Área da equipe: cliente nunca entra; cada seção também confere o papel (o servidor confere em toda chamada). */
export function RequireStaff() {
  const roles = useAuth((s) => s.claims?.roles ?? [])
  const location = useLocation()
  if (!isStaff(roles)) return <Navigate to="/" replace />
  if (location.pathname !== '/equipe' && !canAccess(roles, location.pathname)) return <Navigate to="/equipe" replace />
  return <Outlet />
}

export function NotFound() {
  return (
    <main className="mx-auto max-w-md px-4 py-24 text-center">
      <h1 className="text-2xl font-semibold">Página não encontrada</h1>
      <Link to="/" className="mt-4 inline-block font-medium text-accent hover:underline">
        Voltar ao início
      </Link>
    </main>
  )
}
