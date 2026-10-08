import { useQueryClient } from '@tanstack/react-query'
import { useRefreshMoney } from '../accounts/hooks'

/** Depois de aplicar ou resgatar, a lista de investimentos, o detalhe e os saldos das contas ficam velhos. */
export function useRefreshInvestments() {
  const client = useQueryClient()
  const refreshMoney = useRefreshMoney()
  return () =>
    Promise.all([
      client.invalidateQueries({ queryKey: ['investments'] }),
      client.invalidateQueries({ queryKey: ['investment'] }),
      refreshMoney(),
    ])
}
