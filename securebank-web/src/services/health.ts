export type HealthStatus = 'UP' | 'DOWN' | 'OUT_OF_SERVICE' | 'UNKNOWN'

// O actuator responde 503 com {"status":"DOWN"} quando um componente cai — também é uma resposta válida.
export async function fetchHealth(signal?: AbortSignal): Promise<HealthStatus> {
  const res = await fetch('/api/v1/actuator/health', { signal })
  const body = (await res.json().catch(() => null)) as { status?: HealthStatus } | null
  return body?.status ?? 'UNKNOWN'
}
