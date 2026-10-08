import { useQueryClient } from '@tanstack/react-query'
import { useRefreshMoney } from '../accounts/hooks'

/** Depois de comprar ou vender, carteiras, histórico e saldos das contas ficam velhos. */
export function useRefreshFx() {
  const client = useQueryClient()
  const refreshMoney = useRefreshMoney()
  return () =>
    Promise.all([
      client.invalidateQueries({ queryKey: ['fx'] }),
      client.invalidateQueries({ queryKey: ['fx-wallets'] }),
      refreshMoney(),
    ])
}
