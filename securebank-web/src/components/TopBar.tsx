import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router'
import { Bell } from '@phosphor-icons/react'
import { bankingApi } from '../services/banking'
import { useAuth } from '../stores/auth'
import { initials, titleCase } from '../lib/dashboard'
import { roleLabel } from '../lib/staff'
import { ThemeToggle } from './ThemeToggle'

/** Barra superior (telas largas): avisos, tema e a pessoa logada. No celular, o shell já traz tema e sair. */
export function TopBar() {
  const roles = useAuth((s) => s.claims?.roles ?? [])
  const isCustomer = roles.includes('CUSTOMER')
  const me = useQuery({ queryKey: ['me'], queryFn: bankingApi.me, enabled: isCustomer, staleTime: 5 * 60_000 })
  const unread = useQuery({
    queryKey: ['notifications', 'unread'],
    queryFn: () => bankingApi.notifications(0, 50),
    enabled: isCustomer,
    select: (page) => page.items.filter((n) => !n.read).length,
  })

  const name = isCustomer ? (me.data ? titleCase(me.data.name) : '') : 'Equipe do banco'
  const subtitle = isCustomer ? (me.data?.email ?? '') : roles.map(roleLabel).join(', ')

  return (
    <div className="mb-6 hidden items-center justify-end gap-2 md:flex">
      {isCustomer && (
        <Link
          to="/notificacoes"
          aria-label={unread.data ? `Avisos (${unread.data} novos)` : 'Avisos'}
          className="relative grid size-11 place-items-center rounded-2xl border border-line bg-surface text-muted transition hover:text-ink"
        >
          <Bell size={20} />
          {!!unread.data && <span className="absolute right-2.5 top-2.5 size-2 rounded-full bg-accent ring-2 ring-[var(--panel-solid)]" />}
        </Link>
      )}
      <span className="grid size-11 place-items-center rounded-2xl border border-line bg-surface">
        <ThemeToggle className="p-2" />
      </span>
      <span className="flex items-center gap-3 rounded-2xl border border-line bg-surface py-1.5 pl-1.5 pr-4">
        <span className="grid size-9 place-items-center rounded-xl bg-accent text-sm font-semibold text-accent-fg">{isCustomer ? initials(me.data?.name ?? '') : 'EQ'}</span>
        <span className="leading-tight">
          <span className="block text-sm font-medium">{name || ' '}</span>
          <span className="block max-w-44 truncate text-xs text-muted">{subtitle || ' '}</span>
        </span>
      </span>
    </div>
  )
}
