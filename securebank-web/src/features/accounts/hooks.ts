import { useQuery, useQueryClient } from '@tanstack/react-query'
import { bankingApi } from '../../services/banking'

export const useAccounts = () => useQuery({ queryKey: ['accounts'], queryFn: bankingApi.accounts })

/** Depois de qualquer operação de dinheiro, saldos, extratos e limites ficam velhos. */
export function useRefreshMoney() {
  const client = useQueryClient()
  return () =>
    Promise.all([
      client.invalidateQueries({ queryKey: ['accounts'] }),
      client.invalidateQueries({ queryKey: ['account'] }),
      client.invalidateQueries({ queryKey: ['statement'] }),
      client.invalidateQueries({ queryKey: ['limits'] }),
      client.invalidateQueries({ queryKey: ['notifications'] }),
    ])
}

/** Soma em centavos inteiros (nunca float) e devolve a string "1234.56". */
export function sumAmounts(values: string[]): string {
  const cents = values.reduce((acc, v) => acc + Math.round(Number(v) * 100), 0)
  return (cents / 100).toFixed(2)
}
