import { useState } from 'react'
import { Prohibit, ShieldWarning, WarningOctagon } from '@phosphor-icons/react'
import type { Card } from '../../services/types'
import { composeReason, reasonIssue, reasonOptions, REASON_MAX, type ReasonAction } from '../../lib/card'
import { Alert, Button, Field } from '../../components/ui'
import { Dialog } from '../../components/Dialog'

const copy = {
  block: {
    title: 'Bloquear este cartão?',
    confirm: 'Bloquear cartão',
    icon: <ShieldWarning size={22} weight="duotone" />,
  },
  cancel: {
    title: 'Cancelar este cartão?',
    confirm: 'Cancelar cartão definitivamente',
    icon: <Prohibit size={22} weight="duotone" />,
  },
} as const

/**
 * Confirmação de bloquear ou cancelar: explica o que acontece, pede o motivo (escolher uma opção e, em "Outro motivo",
 * contar com as próprias palavras) e só então envia. O motivo fica no cartão e na auditoria.
 */
export function CardActionDialog({
  card,
  action,
  busy,
  error,
  onClose,
  onConfirm,
}: {
  card: Card
  action: ReasonAction | null
  busy: boolean
  error: string | null
  onClose: () => void
  onConfirm: (action: ReasonAction, reason: string) => void
}) {
  // o estado do formulário vive no conteúdo, que só existe com o diálogo aberto: abrir de novo começa limpo
  return (
    <Dialog
      open={action !== null}
      onClose={onClose}
      busy={busy}
      title={action ? copy[action].title : ''}
      icon={action ? copy[action].icon : undefined}
      footer={
        action && (
          <>
            <Button type="button" variant="secondary" onClick={onClose} disabled={busy}>
              Voltar
            </Button>
            <Button type="submit" form="card-action-form" variant={action === 'cancel' ? 'danger' : 'primary'} loading={busy}>
              {copy[action].confirm}
            </Button>
          </>
        )
      }
    >
      {action && <Body key={action} card={card} action={action} busy={busy} error={error} onConfirm={onConfirm} />}
    </Dialog>
  )
}

function Body({
  card,
  action,
  busy,
  error,
  onConfirm,
}: {
  card: Card
  action: ReasonAction
  busy: boolean
  error: string | null
  onConfirm: (action: ReasonAction, reason: string) => void
}) {
  const options = reasonOptions[action]
  const [choice, setChoice] = useState<string>('')
  const [details, setDetails] = useState('')
  const [touched, setTouched] = useState(false)
  const option = options.find((o) => o.id === choice)
  const issue = reasonIssue(option, details)
  const maxDetails = option && !option.other ? REASON_MAX - option.label.length - 2 : REASON_MAX

  function submit() {
    setTouched(true)
    if (issue || !option) return
    onConfirm(action, composeReason(option, details))
  }

  return (
    <form
      id="card-action-form"
      className="flex flex-col gap-5"
      onSubmit={(e) => {
        e.preventDefault()
        submit()
      }}
      noValidate
    >
      {action === 'cancel' ? (
        <div role="note" className="flex gap-3 rounded-ui bg-warn-soft px-4 py-3 text-sm text-warn">
          <WarningOctagon size={22} weight="duotone" className="mt-0.5 shrink-0" />
          <div className="flex flex-col gap-1.5">
            <p className="font-semibold">Cancelar é definitivo</p>
            <p>O cartão final {card.last4} deixa de funcionar na hora e não pode ser reativado.</p>
            <p>Para voltar a comprar, é só pedir um cartão novo: ele vem com outro número, validade e CVV. Assinaturas e compras recorrentes ligadas a este cartão precisam ser atualizadas.</p>
          </div>
        </div>
      ) : (
        <p className="text-sm text-muted">
          Enquanto o cartão final <span className="num font-medium text-ink">{card.last4}</span> estiver bloqueado, nenhuma compra passa. Você desbloqueia quando quiser.
        </p>
      )}

      {error && <Alert tone="error">{error}</Alert>}

      <fieldset className="flex flex-col gap-2" disabled={busy}>
        <legend className="mb-1 text-sm font-medium">Por que você está {action === 'cancel' ? 'cancelando' : 'bloqueando'}?</legend>
        {options.map((o) => (
          <label
            key={o.id}
            className={`flex cursor-pointer items-center gap-3 rounded-ui border px-4 py-2.5 text-sm transition ${choice === o.id ? 'border-accent bg-accent/10' : 'border-line hover:border-muted/50'}`}
          >
            <input type="radio" name="reason" value={o.id} checked={choice === o.id} onChange={() => setChoice(o.id)} className="size-4 accent-[var(--accent)]" />
            {o.label}
          </label>
        ))}
        {touched && !option && <p className="text-sm text-danger">Escolha o motivo</p>}
      </fieldset>

      {option && (
        <Field
          label={option.other ? 'Conte o que aconteceu' : 'Quer contar mais? (opcional)'}
          htmlFor="reason-details"
          error={touched && option.other ? issue ?? undefined : undefined}
          hint={`${details.trim().length}/${maxDetails}`}
        >
          <textarea
            id="reason-details"
            value={details}
            rows={3}
            maxLength={maxDetails}
            disabled={busy}
            aria-invalid={touched && !!issue}
            onChange={(e) => setDetails(e.target.value.replace(/[\r\n]+/g, ' '))}
            className="field w-full resize-none"
            placeholder={option.other ? 'Escreva em poucas palavras' : 'Algum detalhe que ajude a equipe a entender'}
          />
        </Field>
      )}
    </form>
  )
}
