import { useState } from 'react'
import { Link } from 'react-router'
import { ApiError } from '../../lib/api'
import { InvalidBrCodeError, chargeTxid, decode, looksLikeCode } from '../../lib/brcode'
import { createIdempotency } from '../../lib/idempotency'
import { accountLabel, accountTypeLabel, formatDateTime } from '../../lib/format'
import { messageFor } from '../../lib/errors'
import { formatBRL, parseAmount } from '../../lib/money'
import { displayDay, keyInputError, keyTypeLabel, scheduleBounds, scheduleDateError } from '../../lib/pix'
import { pixApi, type PixChargeView, type PixEntry, type PixLookup, type PixSchedule } from '../../services/pix'
import { Alert, Button, EmptyState, Field, Input, PageHeader, Panel, Skeleton } from '../../components/ui'
import { useAccounts } from '../accounts/hooks'
import { usePixHistory, useRefreshPix } from './hooks'
import { AccountSelect, Receipt } from './parts'

type Target =
  | { kind: 'key'; key: string; lookup: PixLookup; fixedAmount: string | null }
  | { kind: 'charge'; txid: string; view: PixChargeView }

type Result = { kind: 'sent' | 'paid'; entry: PixEntry } | { kind: 'scheduled'; schedule: PixSchedule }

/** Enviar Pix: chave (ou Copia e Cola, inclusive de cobrança) → consulta mascarada → valor → revisão → comprovante. */
export function PixSendPage() {
  const accounts = useAccounts()
  const history = usePixHistory(0, 30)
  const refresh = useRefreshPix()
  const [today] = useState(() => new Date())
  const [idem] = useState(() => createIdempotency())

  const [input, setInput] = useState('')
  const [inputError, setInputError] = useState<string | null>(null)
  const [resolving, setResolving] = useState(false)
  const [target, setTarget] = useState<Target | null>(null)
  const [accountId, setAccountId] = useState('')
  const [amount, setAmount] = useState('')
  const [message, setMessage] = useState('')
  const [scheduleOn, setScheduleOn] = useState(false)
  const [date, setDate] = useState('')
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [review, setReview] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [result, setResult] = useState<Result | null>(null)

  const list = accounts.data ?? []
  const effectiveAccount = accountId || (list.length === 1 ? list[0].id : '')
  const recents = [...new Map((history.data?.items ?? []).filter((e) => e.direction === 'SENT').map((e) => [e.key, e])).values()].slice(0, 5)

  async function resolve(raw: string) {
    const text = raw.trim()
    const problem = keyInputError(text)
    setInputError(problem)
    if (problem) return
    setResolving(true)
    setError(null)
    try {
      if (looksLikeCode(text)) {
        const data = decode(text)
        if (data.key) {
          const fixed = data.amount ? parseAmount(data.amount) : null
          setTarget({ kind: 'key', key: data.key, lookup: await pixApi.lookup(data.key), fixedAmount: fixed })
          if (fixed) setAmount(fixed.replace('.', ','))
        } else {
          // QR dinâmico: nunca acessa a URL do código; só extrai o txid e consulta a PRÓPRIA API.
          const txid = chargeTxid(data.location ?? '')
          if (!txid) throw new InvalidBrCodeError('Este QR de cobrança não é de um banco que o sistema reconheça.')
          const view = await pixApi.charge(txid)
          if (view.own) throw new InvalidBrCodeError('Esta cobrança é sua; quem paga é o outro lado.')
          if (view.status !== 'ACTIVE') throw new InvalidBrCodeError('Esta cobrança não está mais disponível para pagamento.')
          setTarget({ kind: 'charge', txid, view })
          setAmount(view.amount.amount.replace('.', ','))
        }
      } else {
        setTarget({ kind: 'key', key: text, lookup: await pixApi.lookup(text), fixedAmount: null })
      }
    } catch (e) {
      if (e instanceof InvalidBrCodeError) setInputError(e.message)
      else if (e instanceof ApiError && e.status === 404) setInputError('Chave ou cobrança não encontrada. Confira e tente de novo.')
      else setError(messageFor(e))
    } finally {
      setResolving(false)
    }
  }

  function goReview() {
    const next: Record<string, string> = {}
    if (!effectiveAccount) next.account = 'Escolha a conta de origem'
    if (parseAmount(amount) === null) next.amount = 'Informe um valor maior que zero, com até 2 casas decimais'
    if (message.length > 140) next.message = 'No máximo 140 caracteres'
    if (scheduleOn && target?.kind === 'key') {
      const dateProblem = scheduleDateError(date, today)
      if (dateProblem) next.date = dateProblem
    }
    setErrors(next)
    if (Object.keys(next).length === 0) setReview(true)
  }

  async function confirm() {
    if (!target) return
    setError(null)
    setSubmitting(true)
    const value = parseAmount(amount)!
    const text = message.trim() || undefined
    const scheduled = scheduleOn && target.kind === 'key'
    const payload = { accountId: effectiveAccount, target: target.kind === 'charge' ? target.txid : target.key, value, text, date: scheduled ? date : null }
    try {
      const key = idem.keyFor(payload)
      if (target.kind === 'charge') {
        setResult({ kind: 'paid', entry: await pixApi.payCharge(target.txid, effectiveAccount, key) })
      } else if (scheduled) {
        setResult({ kind: 'scheduled', schedule: await pixApi.schedule({ sourceAccountId: effectiveAccount, key: target.key, amount: value, message: text, scheduledFor: date }, key) })
      } else {
        setResult({ kind: 'sent', entry: await pixApi.send({ sourceAccountId: effectiveAccount, key: target.key, amount: value, message: text }, key) })
      }
      idem.settle()
      await refresh()
    } catch (e) {
      if (!(e instanceof ApiError) || !e.uncertain) idem.settle()
      setError(messageFor(e))
      // cotação ou cobrança mudou: volta para os dados para a pessoa conferir
    } finally {
      setSubmitting(false)
    }
  }

  function reset() {
    setInput('')
    setTarget(null)
    setAmount('')
    setMessage('')
    setScheduleOn(false)
    setDate('')
    setErrors({})
    setReview(false)
    setError(null)
    setResult(null)
    setInputError(null)
  }

  if (accounts.isPending) return <Skeleton className="h-64" />
  if (!list.length) {
    return (
      <EmptyState title="Você precisa de uma conta para enviar Pix" action={<Link to="/contas"><Button>Abrir conta</Button></Link>} />
    )
  }

  if (result) {
    const again = <Button onClick={reset}>Novo Pix</Button>
    if (result.kind === 'scheduled') {
      const s = result.schedule
      return (
        <Receipt
          title="Pix agendado"
          amount={s.amount.amount}
          lines={[
            { label: 'Para', value: s.destinationName },
            { label: 'Chave', value: s.key },
            { label: 'Data', value: displayDay(s.scheduledFor) },
            ...(s.message ? [{ label: 'Mensagem', value: s.message }] : []),
            { label: 'Situação', value: 'O dinheiro só sai na data. Se faltar saldo ou limite, o Pix não é feito e você é avisado.' },
          ]}
        >
          {again}
          <Link to="/pix/agendados"><Button variant="secondary">Ver agendados</Button></Link>
        </Receipt>
      )
    }
    const e = result.entry
    return (
      <Receipt
        title={result.kind === 'paid' ? 'Cobrança paga' : 'Pix enviado'}
        amount={e.amount.amount}
        lines={[
          { label: 'Para', value: e.counterpartName },
          { label: result.kind === 'paid' ? 'Cobrança' : 'Chave', value: e.key },
          ...(e.message ? [{ label: 'Mensagem', value: e.message }] : []),
          { label: 'Data', value: formatDateTime(e.createdAt) },
          { label: 'Identificador', value: <span className="num text-xs">{e.endToEndId}</span> },
        ]}
      >
        {again}
        <Link to="/pix/historico"><Button variant="secondary">Ver histórico</Button></Link>
      </Receipt>
    )
  }

  const source = list.find((a) => a.id === effectiveAccount)
  const scheduled = scheduleOn && target?.kind === 'key'
  const bounds = scheduleBounds(today)

  return (
    <>
      <PageHeader title="Enviar Pix" description="Digite a chave (CPF, e-mail, celular ou aleatória) ou cole o Pix Copia e Cola, inclusive o de uma cobrança." />
      <div className="max-w-xl">
        {!target && (
          <div className="flex flex-col gap-5">
            {error && <Alert tone="error">{error}</Alert>}
            <form
              className="flex flex-col gap-4"
              onSubmit={(e) => {
                e.preventDefault()
                void resolve(input)
              }}
            >
              <Field label="Chave ou código" htmlFor="key" error={inputError ?? undefined}>
                <Input id="key" value={input} autoComplete="off" aria-invalid={!!inputError} onChange={(e) => { setInput(e.target.value); setInputError(null) }} />
              </Field>
              <Button type="submit" loading={resolving}>Continuar</Button>
            </form>
            {recents.length > 0 && (
              <div>
                <h2 className="mb-2 text-sm font-medium">Recentes</h2>
                <ul className="card divide-y divide-line overflow-hidden">
                  {recents.map((r) => (
                    <li key={r.key}>
                      <button type="button" disabled={resolving} onClick={() => { setInput(r.key); void resolve(r.key) }} className="block w-full px-5 py-3 text-left transition hover:bg-surface-2">
                        <span className="block text-sm font-medium">{r.counterpartName}</span>
                        <span className="block break-all text-xs text-muted">{r.key}</span>
                      </button>
                    </li>
                  ))}
                </ul>
              </div>
            )}
          </div>
        )}

        {target && !review && (
          <div className="flex flex-col gap-5">
            <Recipient target={target} />
            <Button variant="ghost" className="self-start" onClick={() => { setTarget(null); setAmount(''); setErrors({}) }}>Trocar chave</Button>
            {list.length > 1 && <AccountSelect id="source" label="Enviar da conta" accounts={list} value={effectiveAccount} onChange={setAccountId} error={errors.account} />}
            <Field label="Valor (R$)" htmlFor="amount" error={errors.amount} hint={(target.kind === 'charge' || target.fixedAmount) ? 'Valor definido pelo código' : undefined}>
              <Input id="amount" inputMode="decimal" className="num" placeholder="0,00" autoComplete="off" value={amount} disabled={target.kind === 'charge' || !!target.fixedAmount} aria-invalid={!!errors.amount} onChange={(e) => setAmount(e.target.value)} />
            </Field>
            {target.kind === 'key' && (
              <>
                <Field label="Mensagem (opcional)" htmlFor="message" error={errors.message}>
                  <Input id="message" maxLength={140} value={message} onChange={(e) => setMessage(e.target.value)} />
                </Field>
                <label className="flex items-start gap-3 text-sm">
                  <input type="checkbox" checked={scheduleOn} onChange={(e) => setScheduleOn(e.target.checked)} className="mt-0.5 size-4 accent-[var(--accent)]" />
                  <span>
                    <span className="font-medium">Agendar para outra data</span>
                    <span className="block text-xs text-muted">O dinheiro só sai na data escolhida.</span>
                  </span>
                </label>
                {scheduleOn && (
                  <Field label="Data" htmlFor="date" error={errors.date} hint={`De ${displayDay(bounds.min)} até ${displayDay(bounds.max)}`}>
                    <Input id="date" type="date" min={bounds.min} max={bounds.max} value={date} aria-invalid={!!errors.date} onChange={(e) => setDate(e.target.value)} />
                  </Field>
                )}
              </>
            )}
            <Button onClick={goReview}>Revisar</Button>
          </div>
        )}

        {target && review && (
          <Panel title="Confirme o Pix">
            <dl className="flex flex-col gap-4 text-sm">
              <Line label="Valor" value={<span className="num text-lg font-semibold">{formatBRL(parseAmount(amount)!)}</span>} />
              <Line label="Para" value={target.kind === 'charge' ? target.view.receiverName : target.lookup.name} />
              <Line label={target.kind === 'charge' ? 'Cobrança' : 'Chave'} value={target.kind === 'charge' ? 'paga na hora, uso único' : target.key} />
              <Line label="De" value={source ? `${accountTypeLabel(source.type)}, ${accountLabel(source)}` : ''} />
              {scheduled && <Line label="Data" value={displayDay(date)} />}
              {message.trim() && target.kind === 'key' && <Line label="Mensagem" value={message.trim()} />}
            </dl>
            {error && <div className="mt-5"><Alert tone="error">{error}</Alert></div>}
            <div className="mt-6 flex flex-wrap gap-3">
              <Button loading={submitting} onClick={confirm}>{scheduled ? 'Confirmar agendamento' : 'Confirmar Pix'}</Button>
              <Button variant="secondary" disabled={submitting} onClick={() => { setReview(false); setError(null) }}>Editar</Button>
            </div>
          </Panel>
        )}
      </div>
    </>
  )
}

function Recipient({ target }: { target: Target }) {
  if (target.kind === 'charge') {
    const c = target.view
    return (
      <Panel>
        <p className="text-xs text-muted">Cobrança para</p>
        <p className="mt-1 text-lg font-semibold">{c.receiverName}</p>
        <p className="text-sm text-muted">CPF {c.receiverDocument} · {c.bank}</p>
        {c.description && <p className="mt-2 text-sm">{c.description}</p>}
        <p className="mt-2 text-xs text-muted">Vence em {formatDateTime(c.expiresAt)}</p>
      </Panel>
    )
  }
  const l = target.lookup
  return (
    <Panel>
      <p className="text-xs text-muted">Para</p>
      <p className="mt-1 text-lg font-semibold">{l.name}</p>
      <p className="text-sm text-muted">CPF {l.document} · {l.bank}</p>
      <p className="break-all text-sm text-muted">{keyTypeLabel(l.type)}: {l.key}</p>
      {l.ownAccount && <p className="mt-1 text-sm text-accent">Esta chave é sua.</p>}
    </Panel>
  )
}

function Line({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className="flex justify-between gap-6">
      <dt className="text-muted">{label}</dt>
      <dd className="break-all text-right">{value}</dd>
    </div>
  )
}
