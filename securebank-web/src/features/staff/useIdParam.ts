import { useSearchParams } from 'react-router'

/** Identificador na URL (?id=): permite chegar na tela já com a conta ou o cliente escolhido (links da auditoria). */
export function useIdParam(): [string, (id: string) => void] {
  const [params, setParams] = useSearchParams()
  return [params.get('id') ?? '', (id) => setParams(id ? { id } : {}, { replace: true })]
}
