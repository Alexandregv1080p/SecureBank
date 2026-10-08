import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { Link } from 'react-router'
import { pixApi, type PixKey, type PixKeyType } from '../../services/pix'
import { messageFor } from '../../lib/errors'
import { MAX_KEYS, keyTypeLabel, keyTypes } from '../../lib/pix'
import { Alert, Button, EmptyState, ErrorState, Field, PageHeader, Panel, Select, Skeleton } from '../../components/ui'
import { ConfirmButton } from '../../components/ConfirmButton'
import { useAccounts } from '../accounts/hooks'
import { usePixKeys, useRefreshPix } from './hooks'
import { AccountSelect } from './parts'

/** Chaves Pix: o valor vem do cadastro do cliente (o servidor lê CPF, e-mail e celular), então ninguém registra a chave de outra pessoa. */
export function PixKeysPage() {
  const keys = usePixKeys()
  const accounts = useAccounts()
  const refresh = useRefreshPix()
  const [accountId, setAccountId] = useState('')
  const [type, setType] = useState<PixKeyType>('RANDOM')
  const list = accounts.data ?? []
  const effectiveAccount = accountId || (list.length === 1 ? list[0].id : '')
  const register = useMutation({ mutationFn: () => pixApi.registerKey(effectiveAccount, type), onSuccess: () => refresh() })
  const remove = useMutation({ mutationFn: (k: PixKey) => pixApi.deleteKey(k.id), onSuccess: () => refresh() })
  const full = (keys.data?.length ?? 0) >= MAX_KEYS

  return (
    <>
      <PageHeader title="Minhas chaves" description={`CPF, e-mail, celular ou chave aleatória. Até ${MAX_KEYS} chaves, únicas no banco.`} />
      {keys.isPending && <Skeleton className="h-32" />}
      {keys.isError && <ErrorState message={messageFor(keys.error)} onRetry={() => keys.refetch()} />}
      {keys.data?.length === 0 && <EmptyState title="Você ainda não tem chaves">Cadastre uma para receber Pix sem precisar passar os dados da conta.</EmptyState>}
      {remove.isError && <div className="mb-3"><Alert tone="error">{messageFor(remove.error)}</Alert></div>}
      {!!keys.data?.length && (
        <ul className="card divide-y divide-line overflow-hidden">
          {keys.data.map((k) => (
            <li key={k.id} className="flex flex-wrap items-center justify-between gap-3 px-5 py-4">
              <div className="min-w-0">
                <p className="text-xs text-muted">{keyTypeLabel(k.type)}</p>
                <p className="break-all text-sm font-medium">{k.key}</p>
              </div>
              <ConfirmButton label="Remover" confirmLabel="Confirmar remoção" loading={remove.isPending} onConfirm={() => remove.mutate(k)} />
            </li>
          ))}
        </ul>
      )}

      <div className="mt-8 max-w-md">
        <Panel title="Cadastrar chave">
          {!list.length ? (
            <p className="text-sm text-muted">Você precisa de uma conta. <Link className="text-accent hover:underline" to="/contas">Abrir conta</Link></p>
          ) : full ? (
            <p className="text-sm text-muted">Você já tem o máximo de {MAX_KEYS} chaves. Remova uma para cadastrar outra.</p>
          ) : (
            <div className="flex flex-col gap-4">
              {register.isError && <Alert tone="error">{messageFor(register.error)}</Alert>}
              {list.length > 1 && <AccountSelect id="account" label="Conta que recebe" accounts={list} value={effectiveAccount} onChange={setAccountId} />}
              <Field label="Tipo de chave" htmlFor="type" hint={type === 'RANDOM' ? 'O servidor gera um código aleatório.' : 'O valor é o do seu cadastro; você só escolhe o tipo.'}>
                <Select id="type" value={type} onChange={(e) => setType(e.target.value as PixKeyType)}>
                  {keyTypes.map((t) => <option key={t} value={t}>{keyTypeLabel(t)}</option>)}
                </Select>
              </Field>
              <Button loading={register.isPending} disabled={!effectiveAccount} onClick={() => register.mutate()}>Cadastrar</Button>
            </div>
          )}
        </Panel>
      </div>
    </>
  )
}
