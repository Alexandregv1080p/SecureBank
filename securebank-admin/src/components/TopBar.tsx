import { useAuth } from '../stores/auth'
import { roleLabel } from '../lib/staff'
import { ThemeToggle } from './ThemeToggle'

/** Barra superior (telas largas): tema e o papel de quem está logado. No celular, o shell já traz tema e sair. */
export function TopBar() {
  const roles = useAuth((s) => s.claims?.roles ?? [])
  return (
    <div className="mb-6 hidden items-center justify-end gap-2 md:flex">
      <span className="grid size-11 place-items-center rounded-2xl border border-line bg-surface">
        <ThemeToggle className="p-2" />
      </span>
      <span className="flex items-center gap-3 rounded-2xl border border-line bg-surface py-1.5 pl-1.5 pr-4">
        <span className="grid size-9 place-items-center rounded-xl bg-accent text-sm font-semibold text-accent-fg">EQ</span>
        <span className="leading-tight">
          <span className="block text-sm font-medium">Equipe do banco</span>
          <span className="block max-w-44 truncate text-xs text-muted">{roles.map(roleLabel).join(', ')}</span>
        </span>
      </span>
    </div>
  )
}
