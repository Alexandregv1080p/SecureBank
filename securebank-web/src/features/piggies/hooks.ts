import { useQueryClient } from '@tanstack/react-query'
import { useRefreshMoney } from '../accounts/hooks'

/** Depois de guardar, resgatar, criar ou fechar, a lista e o saldo das contas ficam velhos. */
export function useRefreshPiggies() {
  const client = useQueryClient()
  const refreshMoney = useRefreshMoney()
  return () => Promise.all([client.invalidateQueries({ queryKey: ['piggies'] }), client.invalidateQueries({ queryKey: ['piggy'] }), refreshMoney()])
}
