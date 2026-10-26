import { useEffect, useRef } from 'react'
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router'
import { Bank, ChartLineUp, ClipboardText, CurrencyCircleDollar, House, ShieldCheck, SignOut, UserGear, Users } from '@phosphor-icons/react'
import { logout } from '../services/auth'
import { useAuth } from '../stores/auth'
import { sectionsFor } from '../lib/staff'
import { ThemeToggle } from './ThemeToggle'
import { TopBar } from './TopBar'

const icons: Record<string, typeof House> = {
  '/': ChartLineUp,
  '/auditoria': ClipboardText,
  '/clientes': Users,
  '/contas': Bank,
  '/cambio': CurrencyCircleDollar,
  '/usuarios': UserGear,
}

function Logo() {
  return (
    <span className="flex items-center gap-3">
      <span className="grid size-9 place-items-center rounded-lg bg-accent text-accent-fg">
        <ShieldCheck size={20} weight="fill" />
      </span>
      <span className="leading-tight">
        <span className="block text-lg font-semibold tracking-tight">SecureBank</span>
        <span className="block text-[11px] font-semibold uppercase tracking-[0.18em] text-accent">Back-office</span>
      </span>
    </span>
  )
}

/** Casca do back-office: menu pelos papéis da pessoa (o servidor confere o papel em toda chamada). */
export function AppShell() {
  const navigate = useNavigate()
  const location = useLocation()
  const navRef = useRef<HTMLElement>(null)
  useEffect(() => {
    navRef.current?.querySelector('[aria-current="page"]')?.scrollIntoView({ inline: 'center', block: 'nearest' })
  }, [location.pathname])

  const roles = useAuth((s) => s.claims?.roles ?? [])
  const items = sectionsFor(roles)

  async function signOut() {
    await logout().catch(() => undefined)
    navigate('/entrar', { replace: true })
  }

  return (
    <div className="min-h-dvh border-t-[3px] border-accent md:grid md:grid-cols-[16rem_1fr]">
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

        <p className="hidden px-6 pb-2 text-xs font-medium uppercase tracking-wider text-muted/80 md:block">Gestão</p>
        <nav ref={navRef} aria-label="Principal" className="flex gap-1 overflow-x-auto px-3 pb-3 md:flex-1 md:flex-col md:overflow-y-auto md:overflow-x-visible md:pb-0">
          {items.map((s) => {
            const Icon = icons[s.to] ?? House
            return (
              <NavLink
                key={s.to}
                to={s.to}
                end={s.to === '/'}
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
                    {s.label}
                  </>
                )}
              </NavLink>
            )
          })}
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
