import { useEffect, useId, useRef, type ReactNode } from 'react'

interface DialogProps {
  open: boolean
  /** Esc, clique fora ou o botão de fechar. Ignorado enquanto `busy` (uma operação está em andamento). */
  onClose: () => void
  title: string
  icon?: ReactNode
  busy?: boolean
  children: ReactNode
  footer?: ReactNode
}

/**
 * Janela modal sobre o `<dialog>` nativo: o navegador cuida do foco preso dentro dela, do Esc e de deixar o resto da página
 * inerte; aqui entram só o visual, o fechar ao clicar fora e o título ligado por aria-labelledby.
 */
export function Dialog({ open, onClose, title, icon, busy = false, children, footer }: DialogProps) {
  const ref = useRef<HTMLDialogElement>(null)
  const titleId = useId()

  useEffect(() => {
    const dialog = ref.current
    if (!dialog) return
    if (open && !dialog.open) dialog.showModal()
    if (!open && dialog.open) dialog.close()
  }, [open])

  return (
    <dialog
      ref={ref}
      aria-labelledby={titleId}
      onCancel={(e) => {
        e.preventDefault() // o fechamento passa pelo estado do React, não pelo navegador
        if (!busy) onClose()
      }}
      onClick={(e) => {
        if (e.target === ref.current && !busy) onClose()
      }}
      className="app-dialog m-auto w-[min(92vw,32rem)] rounded-2xl border border-line bg-surface p-0 text-ink shadow-2xl"
    >
      {open && (
        <div className="flex max-h-[88vh] flex-col">
          <header className="flex items-start gap-3 px-6 pb-2 pt-6">
            {icon && <span className="grid size-10 shrink-0 place-items-center rounded-xl bg-accent-soft text-accent">{icon}</span>}
            <h2 id={titleId} className="pt-1.5 text-lg font-semibold tracking-tight">
              {title}
            </h2>
          </header>
          <div className="overflow-y-auto px-6 pb-4 pt-2">{children}</div>
          {footer && <footer className="flex flex-wrap justify-end gap-3 border-t border-line bg-surface-2/60 px-6 py-4">{footer}</footer>}
        </div>
      )}
    </dialog>
  )
}
