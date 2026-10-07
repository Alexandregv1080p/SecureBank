import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { staffApi, type FxRateAdmin } from '../../services/staff'
import { formatDateTime } from '../../lib/format'
import { messageFor } from '../../lib/errors'
import { parseRate, parseSpread } from '../../lib/staff'
import { Alert, Button, ErrorState, Field, Input, PageHeader, Panel, Skeleton } from '../../components/ui'

const names: Record<string, string> = { USD: 'Dólar americano', EUR: 'Euro' }

/** 5,2780: quatro casas, vírgula decimal. */
const rate4 = (n: number | string) => Number(n).toLocaleString('pt-BR', { minimumFractionDigits: 4, maximumFractionDigits: 4 })

export function FxRatesPage() {
  const rates = useQuery({ queryKey: ['staff', 'fx'], queryFn: staffApi.fxRates })
  return (
    <>
      <PageHeader title="Câmbio" description="Cotação comercial e spread de cada moeda. O cliente compra por cotação + spread e vende por cotação − spread. A mudança vale só para operações novas." />
      {rates.isPending && <Skeleton className="h-48" />}
      {rates.isError && <ErrorState message={messageFor(rates.error)} onRetry={() => rates.refetch()} />}
      <div className="flex flex-col gap-6">
        {rates.data?.map((r) => <RateCard key={`${r.currency}-${r.updatedAt}`} rate={r} />)}
      </div>
    </>
  )
}

function RateCard({ rate }: { rate: FxRateAdmin }) {
  const client = useQueryClient()
  const [mid, setMid] = useState(Number(rate.mid).toString().replace('.', ','))
  const [spread, setSpread] = useState(rate.spreadPercent.replace('.', ','))
  const midValue = parseRate(mid)
  const spreadValue = parseSpread(spread)
  const valid = !!midValue && !!spreadValue
  const jump = midValue ? Math.abs(Number(midValue) - Number(rate.mid)) / Number(rate.mid) : 0
  const save = useMutation({
    mutationFn: () => staffApi.changeFxRate(rate.currency, midValue!, spreadValue!),
    onSuccess: () => client.invalidateQueries({ queryKey: ['staff', 'fx'] }),
  })
  const preview = valid ? { buy: rate4(Number(midValue) * (1 + Number(spreadValue) / 100)), sell: rate4(Number(midValue) * (1 - Number(spreadValue) / 100)) } : null

  return (
    <Panel title={`${names[rate.currency] ?? rate.currency} (${rate.currency})`}>
      <div className="flex flex-col gap-4">
        <p className="text-sm text-muted">
          Hoje: comercial R$ {rate4(rate.mid)} · cliente compra por R$ {rate4(rate.buyRate)} e vende por R$ {rate4(rate.sellRate)} · atualizada em {formatDateTime(rate.updatedAt)}
        </p>
        <div className="grid gap-3 sm:grid-cols-[1fr_1fr_auto] sm:items-end">
          <Field label="Cotação comercial (R$)" htmlFor={`${rate.currency}-mid`} error={!midValue ? 'Informe um valor positivo.' : undefined}>
            <Input id={`${rate.currency}-mid`} inputMode="decimal" value={mid} onChange={(e) => setMid(e.target.value)} aria-invalid={!midValue} />
          </Field>
          <Field label="Spread (%)" htmlFor={`${rate.currency}-spread`} error={!spreadValue ? 'De 0 a 10, até 2 casas.' : undefined}>
            <Input id={`${rate.currency}-spread`} inputMode="decimal" value={spread} onChange={(e) => setSpread(e.target.value)} aria-invalid={!spreadValue} />
          </Field>
          <Button loading={save.isPending} disabled={!valid} onClick={() => save.mutate()}>Salvar</Button>
        </div>
        {preview && <p className="text-xs text-muted">Ficará: cliente compra por R$ {preview.buy} e vende por R$ {preview.sell}.</p>}
        {jump > 0.2 && <Alert tone="info">Salto de {(jump * 100).toFixed(1)}%: o servidor só aceita até 20% de uma vez. Faça em etapas.</Alert>}
        {save.isError && <Alert tone="error">{messageFor(save.error)}</Alert>}
        {save.isSuccess && <Alert tone="success">Cotação atualizada.</Alert>}
      </div>
    </Panel>
  )
}
