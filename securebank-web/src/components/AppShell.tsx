import { useEffect, useRef } from 'react'
import { useQuery } from '@tanstack/react-query'
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router'
import {
  ArrowsLeftRight,
  Bank,
  Barcode,
  Bell,
  ClipboardText,
  CurrencyCircleDollar,
  House,
  PixLogo,
  ShieldCheck,
  SignOut,
  UserGear,
  Users,
  Wallet,
} from '@phosphor-icons/react'
import { bankingApi } from '../services/banking'
import { logout } from '../services/auth'
import { useAuth } from '../stores/auth'
import { sectionsFor } from '../lib/staff'
import { ThemeToggle } from './ThemeToggle'
import { TopBar } from './TopBar'

const nav = [
  { to: '/', label: 'Início', icon: House, end: true },
  { to: '/contas', label: 'Contas', icon: Wallet },
  { to: '/pix', label: 'Pix', icon: PixLogo },
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

/** Marca: um escudo no quadrado com o acento, para o app ter cara própria já na primeira olhada. */
function Logo() {
  return (
    <span className="flex items-center gap-3">
      <span className="grid size-9 place-items-center rounded-xl bg-accent text-accent-fg shadow-[0_8px_20px_-8px_var(--accent)]">
        <ShieldCheck size={20} weight="fill" />
      </span>
      <span className="text-lg font-semibold tracking-tight">SecureBank</span>
    </span>
  )
}

export function AppShell() {
  const navigate = useNavigate()
  const location = useLocation()
  const navRef = useRef<HTMLElement>(null)
  // No celular o menu rola de lado: leva a aba ativa para a vista (senão "Contas e limites" fica escondida).
  useEffect(() => {
    navRef.current?.querySelector('[aria-current="page"]')?.scrollIntoView({ inline: 'center', block: 'nearest' })
  }, [location.pathname])

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
    <div className="min-h-dvh md:grid md:grid-cols-[17rem_1fr]">
      <a href="#conteudo" className="sr-only focus:not-sr-only focus:fixed focus:left-3 focus:top-3 focus:z-50 focus:rounded-ui focus:bg-accent focus:px-3 focus:py-2 focus:text-accent-fg">
        Ir para o conteúdo
      </a>
      <aside className="border-b border-line bg-[color-mix(in_srgb,var(--panel-solid)_55%,transparent)] backdrop-blur-xl md:sticky md:top-0 md:flex md:h-dvh md:flex-col md:border-b-0 md:border-r">
        <div className="flex h-16 items-center justify-between px-5 md:h-20 md:px-6">
          <Logo />
          <span className="flex items-center gap-1 md:hidden">
            <ThemeToggle className="p-2" />
            <button onClick={signOut} className="rounded-ui p-2 text-muted transition hover:bg-surface-2 hover:text-ink" aria-label="Sair">
              <SignOut size={20} />
            </button>
          </span>
        </div>

        <p className="hidden px-6 pb-2 text-xs font-medium uppercase tracking-wider text-muted/80 md:block">Menu</p>
        <nav ref={navRef} aria-label="Principal" className="flex gap-1 overflow-x-auto px-3 pb-3 md:flex-1 md:flex-col md:overflow-y-auto md:overflow-x-visible md:pb-0">
          {items.map(({ to, label, icon: Icon, end }) => (
            <NavLink
              key={to}
              to={to}
              end={end}
              className={({ isActive }) =>
                `group relative flex shrink-0 items-center gap-3 rounded-ui px-3 py-2.5 text-sm font-medium transition duration-200 ${
                  isActive ? 'bg-accent-soft text-ink' : 'text-muted hover:bg-surface-2 hover:text-ink'
                }`
              }
            >
              {({ isActive }) => (
                <>
                  {isActive && <span aria-hidden className="absolute left-0 top-1/2 hidden h-5 w-1 -translate-y-1/2 rounded-r-full bg-accent md:block" />}
                  <Icon size={20} weight={isActive ? 'fill' : 'regular'} className={isActive ? 'text-accent' : ''} />
                  {label}
                  {to === '/notificacoes' && !!unread.data && (
                    <span className="ml-auto rounded-md bg-accent px-1.5 text-xs font-semibold text-accent-fg">{unread.data}</span>
                  )}
                </>
              )}
            </NavLink>
          ))}
        </nav>

        <div className="hidden border-t border-line p-3 md:block">
          <button onClick={signOut} className="flex w-full items-center gap-3 rounded-ui px-3 py-2.5 text-sm font-medium text-muted transition hover:bg-surface-2 hover:text-ink">
            <SignOut size={20} />
            Sair
          </button>
        </div>
      </aside>
      <main id="conteudo" className="mx-auto w-full max-w-[84rem] px-4 py-8 md:px-10 md:py-10">
        <TopBar />
        <Outlet />
      </main>
    </div>
  )
}
