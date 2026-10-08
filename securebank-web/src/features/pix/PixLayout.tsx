import { useEffect, useRef } from 'react'
import { NavLink, Outlet, useLocation } from 'react-router'

const tabs = [
  { to: '/pix', label: 'Visão geral', end: true },
  { to: '/pix/enviar', label: 'Enviar' },
  { to: '/pix/receber', label: 'Receber' },
  { to: '/pix/cobrancas', label: 'Cobranças' },
  { to: '/pix/agendados', label: 'Agendados' },
  { to: '/pix/chaves', label: 'Chaves' },
  { to: '/pix/historico', label: 'Histórico' },
]

/** Moldura do Pix: título e abas; cada aba é uma rota. */
export function PixLayout() {
  const location = useLocation()
  const nav = useRef<HTMLElement>(null)
  // No celular as abas rolam de lado: leva a ativa para a vista.
  useEffect(() => {
    nav.current?.querySelector('[aria-current="page"]')?.scrollIntoView({ inline: 'center', block: 'nearest' })
  }, [location.pathname])
  return (
    <>
      <nav ref={nav} aria-label="Pix" className="no-print -mx-1 mb-8 flex gap-1 overflow-x-auto px-1 pb-1">
        {tabs.map((t) => (
          <NavLink
            key={t.to}
            to={t.to}
            end={t.end}
            className={({ isActive }) =>
              `shrink-0 rounded-full px-4 py-2 text-sm font-medium transition ${isActive ? 'bg-accent text-accent-fg' : 'border border-line bg-surface text-muted hover:text-ink'}`
            }
          >
            {t.label}
          </NavLink>
        ))}
      </nav>
      <Outlet />
    </>
  )
}
