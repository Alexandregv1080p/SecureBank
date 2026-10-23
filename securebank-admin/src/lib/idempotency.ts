/**
 * Uma Idempotency-Key por intenção. Reaproveita a chave só enquanto o pedido é idêntico E o resultado anterior foi
 * incerto (rede caiu, 5xx): assim o retry não duplica a operação. Depois de um resultado definitivo (sucesso ou erro
 * de negócio) a chave é trocada, senão a API reproduziria a resposta antiga para uma nova tentativa.
 */
export function createIdempotency(newKey: () => string = () => crypto.randomUUID()) {
  let key: string | null = null
  let fingerprint: string | null = null
  return {
    keyFor(payload: unknown): string {
      const current = JSON.stringify(payload)
      if (key === null || fingerprint !== current) {
        key = newKey()
        fingerprint = current
      }
      return key
    },
    /** Chame quando a resposta foi definitiva. */
    settle() {
      key = null
      fingerprint = null
    },
  }
}
