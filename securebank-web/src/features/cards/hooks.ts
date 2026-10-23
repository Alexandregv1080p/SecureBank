import { useQueryClient } from '@tanstack/react-query'
import { useRefreshMoney } from '../accounts/hooks'

/** Depois de emitir, bloquear, cancelar ou comprar, a lista de cartões e o saldo das contas ficam velhos. */
export function useRefreshCards() {
  const client = useQueryClient()
  const refreshMoney = useRefreshMoney()
  return () =>
    Promise.all([
      client.invalidateQueries({ queryKey: ['cards'] }),
      client.invalidateQueries({ queryKey: ['card'] }),
      client.invalidateQueries({ queryKey: ['card-purchases'] }),
      client.invalidateQueries({ queryKey: ['credit-summary'] }),
      client.invalidateQueries({ queryKey: ['credit-invoices'] }),
      client.invalidateQueries({ queryKey: ['credit-invoice'] }),
      client.invalidateQueries({ queryKey: ['credit-purchases'] }),
      refreshMoney(),
    ])
}
