import { useQuery, useQueryClient } from '@tanstack/react-query'
import { pixApi } from '../../services/pix'
import { useRefreshMoney } from '../accounts/hooks'

export const usePixKeys = () => useQuery({ queryKey: ['pix', 'keys'], queryFn: pixApi.keys })

export const usePixHistory = (page = 0, size = 10) =>
  useQuery({ queryKey: ['pix', 'history', page, size], queryFn: () => pixApi.history(page, size) })

/** Depois de qualquer operação de Pix, histórico, cobranças, agendados e saldos ficam velhos. */
export function useRefreshPix() {
  const client = useQueryClient()
  const refreshMoney = useRefreshMoney()
  return () => Promise.all([client.invalidateQueries({ queryKey: ['pix'] }), refreshMoney()])
}
