import { decodeClaims } from './jwt'
import { useAuth } from '../stores/auth'

export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly traceId?: string
  readonly network: boolean

  constructor(status: number, code: string, message: string, traceId?: string, network = false) {
    super(message)
    this.status = status
    this.code = code
    this.traceId = traceId
    this.network = network
  }

  /** Resultado incerto: a operação pode ou não ter sido executada (vale reutilizar a Idempotency-Key). */
  get uncertain() {
    return this.network || this.status >= 500 || this.status === 429 || this.status === 409
  }
}

interface Options {
  method?: string
  body?: unknown
  headers?: Record<string, string>
  /** Rotas de autenticação não tentam renovar a sessão nem reenviam o token. */
  anonymous?: boolean
}

let refreshing: Promise<boolean> | null = null

/** Renova o access token com o cookie HttpOnly. Uma renovação por vez, mesmo com várias requisições em 401. */
export function refreshSession(): Promise<boolean> {
  refreshing ??= (async () => {
    try {
      const res = await fetch('/api/v1/auth/refresh', { method: 'POST', headers: { 'X-Client': 'web' } })
      if (!res.ok) return false
      const data = (await res.json()) as { accessToken: string }
      useAuth.getState().setSession(data.accessToken, decodeClaims(data.accessToken))
      return true
    } catch {
      return false
    } finally {
      refreshing = null
    }
  })()
  return refreshing
}

async function send(path: string, options: Options): Promise<Response> {
  const headers: Record<string, string> = { 'X-Client': 'web', ...options.headers }
  if (options.body !== undefined) headers['Content-Type'] = 'application/json'
  const token = useAuth.getState().accessToken
  if (token && !options.anonymous) headers.Authorization = `Bearer ${token}`
  try {
    return await fetch(`/api/v1${path}`, {
      method: options.method ?? 'GET',
      headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
    })
  } catch {
    throw new ApiError(0, 'NETWORK', 'Sem conexão', undefined, true)
  }
}

export async function api<T>(path: string, options: Options = {}): Promise<T> {
  let res = await send(path, options)
  if (res.status === 401 && !options.anonymous && (await refreshSession())) {
    res = await send(path, options)
  }
  if (res.status === 401 && !options.anonymous) {
    useAuth.getState().clear()
  }
  if (res.status === 204) return undefined as T
  const text = await res.text()
  const json = text ? JSON.parse(text) : null
  if (!res.ok) {
    throw new ApiError(res.status, json?.code ?? 'ERROR', json?.message ?? 'Erro', json?.traceId)
  }
  return json as T
}
