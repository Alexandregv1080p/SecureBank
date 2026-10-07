import { useQuery } from '@tanstack/react-query'
import { NavLink, Outlet, useNavigate } from 'react-router'
import { ArrowsLeftRight, Barcode, Bell, ClipboardText, CurrencyCircleDollar, House, ShieldCheck, SignOut, UserGear, Users, Wallet, Bank } from '@phosphor-icons/react'
import { bankingApi } from '../services/banking'
import { logout } from '../services/auth'
import { sectionsFor } from '../lib/staff'
import { useAuth } from '../stores/auth'

const nav = [
  { to: '/', label: 'Início', icon: House, end: true },
  { to: '/contas', label: 'Contas', icon: Wallet },
  { to: '/transferir', label: 'Transferir', icon: ArrowsLeftRight },
  { to: '/pagar', label: 'Pagar', icon: Barcode },
  { to: '/notificacoes', label: 'Avisos', icon: Bell },
  { to: '/seguranca', label: 'Segurança', icon: ShieldCheck },
]

const staffIcons: Record<string, typeof House> = {
  '/equipe/auditoria': ClipboardText,
  '/equipe/clientes': Users,
  '/equipe/contas': Bank,
  '/equipe/cambio': CurrencyCircleDollar,
  '/equipe/usuarios': UserGear,
}

export function AppShell() {
  const navigate = useNavigate()
  const roles = useAuth((s) => s.claims?.roles ?? [])
  const isCustomer = roles.includes('CUSTOMER')
  const unread = useQuery({
    queryKey: ['notifications', 'unread'],
    queryFn: () => bankingApi.notifications(0, 50),
    enabled: isCustomer,
    refetchInterval: 30_000,
    select: (page) => page.items.filter((n) => !n.read).length,
  })

  async function signOut() {
    await logout().catch(() => undefined)
    navigate('/entrar', { replace: true })
  }

  const items = isCustomer
    ? nav
    : [
        { to: '/equipe', label: 'Painel', icon: House, end: true },
        ...sectionsFor(roles).map((s) => ({ to: s.to, label: s.label, icon: staffIcons[s.to] ?? House, end: false })),
        nav[nav.length - 1],
      ]

  return (
    <div className="min-h-dvh md:grid md:grid-cols-[15rem_1fr]">
      <aside className="border-b border-line bg-surface md:sticky md:top-0 md:h-dvh md:border-b-0 md:border-r">
        <div className="flex h-16 items-center justify-between px-5 md:h-20">
          <span className="text-lg font-semibold tracking-tight">SecureBank</span>
          <button onClick={signOut} className="rounded-ui p-2 text-muted hover:bg-accent-soft hover:text-ink md:hidden" aria-label="Sair">
            <SignOut size={20} />
          </button>
        </div>
        <nav aria-label="Principal" className="flex gap-1 overflow-x-auto px-3 pb-3 md:flex-col md:overflow-visible md:pb-0">
          {items.map(({ to, label, icon: Icon, end }) => (
            <NavLink
              key={to}
              to={to}
              end={end}
              className={({ isActive }) =>
                `flex shrink-0 items-center gap-3 rounded-ui px-3 py-2.5 text-sm font-medium transition ${
                  isActive ? 'bg-accent-soft text-ink' : 'text-muted hover:bg-bg hover:text-ink'
                }`
              }
            >
              <Icon size={20} weight="regular" />
              {label}
              {to === '/notificacoes' && !!unread.data && (
                <span className="ml-auto rounded-md bg-accent px-1.5 text-xs font-semibold text-accent-fg">{unread.data}</span>
              )}
            </NavLink>
          ))}
        </nav>
        <button
          onClick={signOut}
          className="absolute bottom-6 left-3 hidden w-[13.5rem] items-center gap-3 rounded-ui px-3 py-2.5 text-sm font-medium text-muted hover:bg-bg hover:text-ink md:flex"
        >
          <SignOut size={20} />
          Sair
        </button>
      </aside>
      <main className="mx-auto w-full max-w-5xl px-4 py-8 md:px-10 md:py-10">
        <Outlet />
      </main>
    </div>
  )
}
