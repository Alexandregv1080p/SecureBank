import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router'
import { bankingApi } from '../../services/banking'
import { encode } from '../../lib/brcode'
import { parseAmount } from '../../lib/money'
import { keyTypeLabel } from '../../lib/pix'
import { amountIssue, sanitizeAmount } from '../../lib/validation'
import { Button, EmptyState, Field, Input, PageHeader, Panel, Select, Skeleton } from '../../components/ui'
import { usePixKeys } from './hooks'
import { CopyButton, QrImage } from './parts'

/** Receber: escolhe a chave e, se quiser, o valor; o QR e o Pix Copia e Cola são gerados aqui, no navegador. */
export function PixReceivePage() {
  const keys = usePixKeys()
  const me = useQuery({ queryKey: ['me'], queryFn: bankingApi.me, staleTime: 5 * 60_000 })
  const [keyId, setKeyId] = useState('')
  const [amount, setAmount] = useState('')

  if (keys.isPending || me.isPending) return <Skeleton className="h-64" />
  if (!keys.data?.length) {
    return (
      <>
        <PageHeader title="Receber Pix" />
        <EmptyState title="Cadastre uma chave para receber" action={<Link to="/pix/chaves"><Button>Cadastrar chave</Button></Link>}>
          Sem chave não há para onde o dinheiro ir.
        </EmptyState>
      </>
    )
  }

  const key = keys.data.find((k) => k.id === keyId) ?? keys.data[0]
  const parsed = amount.trim() ? parseAmount(amount) : null
  const amountError = amount.trim() && parsed === null ? (amountIssue(amount) ?? undefined) : undefined
  const code = amountError ? null : encode(key.key, me.data?.name ?? '', { amount: parsed })

  return (
    <>
      <PageHeader title="Receber Pix" description="Mostre o QR code ou envie o código. O valor é opcional: sem ele, quem paga digita." />
      <div className="grid gap-6 lg:grid-cols-[1fr_20rem]">
        <div className="flex max-w-xl flex-col gap-5">
          {keys.data.length > 1 && (
            <Field label="Receber na chave" htmlFor="key">
              <Select id="key" value={key.id} onChange={(e) => setKeyId(e.target.value)}>
                {keys.data.map((k) => (
                  <option key={k.id} value={k.id}>{keyTypeLabel(k.type)}: {k.key}</option>
                ))}
              </Select>
            </Field>
          )}
          {keys.data.length === 1 && <p className="text-sm"><span className="text-muted">{keyTypeLabel(key.type)}:</span> <span className="break-all font-medium">{key.key}</span></p>}
          <Field label="Valor (opcional)" htmlFor="amount" error={amountError} hint="Em branco: quem paga digita o valor">
            <Input id="amount" inputMode="decimal" className="num" placeholder="0,00" autoComplete="off" value={amount} aria-invalid={!!amountError} onChange={(e) => setAmount(sanitizeAmount(e.target.value))} />
          </Field>
          {code && (
            <>
              <div>
                <h2 className="mb-2 text-sm font-medium">Pix Copia e Cola</h2>
                <p className="card break-all p-4 text-xs text-muted">{code}</p>
              </div>
              <div><CopyButton text={code} /></div>
            </>
          )}
        </div>
        {code && (
          <Panel>
            <QrImage value={code} />
            <p className="mt-3 text-center text-xs text-muted">Aponte a câmera do app do banco para pagar.</p>
          </Panel>
        )}
      </div>
    </>
  )
}
