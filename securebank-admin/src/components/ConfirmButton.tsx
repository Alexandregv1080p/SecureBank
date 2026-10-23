import { useState } from 'react'
import { Button } from './ui'

/** Ação sensível em dois passos: o primeiro clique pede confirmação, para ninguém bloquear uma conta sem querer. */
export function ConfirmButton({
  label,
  confirmLabel = 'Confirmar',
  variant = 'danger',
  loading,
  onConfirm,
}: {
  label: string
  confirmLabel?: string
  variant?: 'danger' | 'secondary'
  loading?: boolean
  onConfirm: () => void
}) {
  const [asking, setAsking] = useState(false)
  if (!asking) {
    return (
      <Button variant={variant} onClick={() => setAsking(true)}>
        {label}
      </Button>
    )
  }
  return (
    <span className="inline-flex items-center gap-2">
      <Button
        variant={variant}
        loading={loading}
        onClick={() => {
          setAsking(false)
          onConfirm()
        }}
      >
        {confirmLabel}
      </Button>
      <Button variant="ghost" onClick={() => setAsking(false)}>
        Cancelar
      </Button>
    </span>
  )
}
