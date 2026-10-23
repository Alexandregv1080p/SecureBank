import { useState } from 'react'
import { Moon, Sun } from '@phosphor-icons/react'

type Theme = 'dark' | 'light'

const current = (): Theme => (document.documentElement.dataset.theme === 'light' ? 'light' : 'dark')

/** Alterna entre escuro e claro e lembra a escolha (o /theme.js a reaplica antes da primeira pintura). */
export function ThemeToggle({ className = '', label = false }: { className?: string; label?: boolean }) {
  const [theme, setTheme] = useState<Theme>(current)

  function toggle() {
    const next: Theme = theme === 'dark' ? 'light' : 'dark'
    document.documentElement.dataset.theme = next
    try {
      localStorage.setItem('sb-theme', next)
    } catch {
      // sem armazenamento (janela privada): o tema vale só nesta sessão
    }
    setTheme(next)
  }

  const Icon = theme === 'dark' ? Sun : Moon
  const text = theme === 'dark' ? 'Tema claro' : 'Tema escuro'
  return (
    <button
      type="button"
      onClick={toggle}
      aria-label={text}
      title={text}
      className={`inline-flex items-center gap-3 rounded-ui text-sm font-medium text-muted transition hover:bg-surface-2 hover:text-ink ${className}`}
    >
      <Icon size={20} />
      {label && text}
    </button>
  )
}
