import { useState, type ReactNode } from 'react'
import { useQuery } from '@tanstack/react-query'
import QRCode from 'qrcode'
import { CheckCircle, Copy, Printer } from '@phosphor-icons/react'
import type { Account } from '../../services/types'
import { accountTypeLabel } from '../../lib/format'
import { formatBRL } from '../../lib/money'
import { Button, Field, Select, Skeleton } from '../../components/ui'

/** Escolha da conta; com uma conta só, mostra qual é em vez de um seletor vazio de opções. */
export function AccountSelect({ id, label, accounts, value, onChange, error }: { id: string; label: string; accounts: Account[]; value: string; onChange: (v: string) => void; error?: string }) {
  return (
    <Field label={label} htmlFor={id} error={error}>
      <Select id={id} value={value} aria-invalid={!!error} onChange={(e) => onChange(e.target.value)}>
        <option value="">Selecione</option>
        {accounts.map((a) => (
          <option key={a.id} value={a.id}>
            {accountTypeLabel(a.type)}, {a.accountNumber} ({formatBRL(a.balance.amount)})
          </option>
        ))}
      </Select>
    </Field>
  )
}

/** QR code do texto, sempre preto sobre branco (os leitores precisam do contraste, mesmo no tema escuro). */
export function QrImage({ value }: { value: string }) {
  const qr = useQuery({
    queryKey: ['qr', value],
    queryFn: () => QRCode.toDataURL(value, { margin: 1, width: 288, errorCorrectionLevel: 'M' }),
    staleTime: Infinity,
  })
  if (!qr.data) return <Skeleton className="mx-auto size-60" />
  return <img src={qr.data} alt="QR code do Pix" width={240} height={240} className="mx-auto size-60 rounded-2xl bg-white p-2" />
}

/** Copia o texto e confirma por alguns segundos; sem permissão de área de transferência, avisa. */
export function CopyButton({ text, label = 'Copiar código' }: { text: string; label?: string }) {
  const [state, setState] = useState<'idle' | 'ok' | 'fail'>('idle')
  async function copy() {
    try {
      await navigator.clipboard.writeText(text)
      setState('ok')
    } catch {
      setState('fail')
    }
    setTimeout(() => setState('idle'), 2500)
  }
  return (
    <Button variant="secondary" onClick={copy}>
      <Copy size={18} /> {state === 'ok' ? 'Copiado' : state === 'fail' ? 'Não foi possível copiar' : label}
    </Button>
  )
}

export interface ReceiptLine {
  label: string
  value: ReactNode
}

/** Comprovante: na tela é um cartão; ao imprimir (ou salvar em PDF) vira só esta folha, em preto sobre branco. */
export function Receipt({ title, amount, lines, children }: { title: string; amount: string; lines: ReceiptLine[]; children?: ReactNode }) {
  return (
    <div className="mx-auto max-w-md pt-4">
      <div className="print-area card p-8">
        <div className="flex items-center justify-between">
          <span className="font-semibold tracking-tight">SecureBank</span>
          <span className="text-xs text-muted">Comprovante</span>
        </div>
        <div className="mt-6 text-center">
          <CheckCircle size={44} weight="duotone" className="mx-auto text-ok" />
          <h1 className="mt-3 text-xl font-semibold">{title}</h1>
          <p className="num mt-2 text-3xl font-semibold">{formatBRL(amount)}</p>
        </div>
        <dl className="mt-6 flex flex-col gap-3 border-t border-line pt-5 text-sm">
          {lines.map((l) => (
            <div key={l.label} className="flex justify-between gap-6">
              <dt className="shrink-0 text-muted">{l.label}</dt>
              <dd className="break-all text-right">{l.value}</dd>
            </div>
          ))}
        </dl>
      </div>
      <div className="no-print mt-6 flex flex-wrap justify-center gap-3">
        <Button variant="secondary" onClick={() => window.print()}>
          <Printer size={18} /> Imprimir ou salvar PDF
        </Button>
        {children}
      </div>
    </div>
  )
}
