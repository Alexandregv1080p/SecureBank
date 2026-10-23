import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { api, ApiError } from './api'
import { messageFor } from './errors'
import { useAuth } from '../stores/auth'

function jwt(sub = 'user-1') {
  const payload = btoa(JSON.stringify({ sub, roles: ['CUSTOMER'], sid: 's1', exp: 9999999999 }))
  return `x.${payload}.y`
}

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })

let fetchMock: ReturnType<typeof vi.fn>

beforeEach(() => {
  fetchMock = vi.fn()
  vi.stubGlobal('fetch', fetchMock)
  useAuth.setState({ accessToken: 'old-token', claims: null, status: 'authenticated' })
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('api', () => {
  it('envia o bearer e o cabeçalho de cliente web', async () => {
    fetchMock.mockResolvedValueOnce(json(200, { ok: true }))

    await expect(api('/accounts')).resolves.toEqual({ ok: true })

    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/v1/accounts')
    expect(init.headers.Authorization).toBe('Bearer old-token')
    expect(init.headers['X-Client']).toBe('web')
  })

  it('em 401 renova a sessão uma vez e repete a requisição', async () => {
    fetchMock
      .mockResolvedValueOnce(json(401, { code: 'UNAUTHENTICATED' }))
      .mockResolvedValueOnce(json(200, { accessToken: jwt() })) // POST /auth/refresh
      .mockResolvedValueOnce(json(200, { items: [] }))

    await expect(api('/accounts')).resolves.toEqual({ items: [] })

    expect(fetchMock.mock.calls[1][0]).toBe('/api/v1/auth/refresh')
    expect(fetchMock.mock.calls[2][1].headers.Authorization).toBe(`Bearer ${jwt()}`)
    expect(useAuth.getState().status).toBe('authenticated')
  })

  it('várias requisições em 401 ao mesmo tempo disparam uma única renovação', async () => {
    let refreshCalls = 0
    fetchMock.mockImplementation(async (url: string, init: { headers: Record<string, string> }) => {
      if (url === '/api/v1/auth/refresh') {
        refreshCalls++
        await new Promise((r) => setTimeout(r, 10))
        return json(200, { accessToken: jwt() })
      }
      return init.headers.Authorization === `Bearer ${jwt()}` ? json(200, { ok: true }) : json(401, {})
    })

    await Promise.all([api('/a'), api('/b'), api('/c')])

    expect(refreshCalls).toBe(1)
  })

  it('se a renovação falha, limpa a sessão e devolve o 401', async () => {
    fetchMock
      .mockResolvedValueOnce(json(401, { code: 'UNAUTHENTICATED', message: 'Authentication required' }))
      .mockResolvedValueOnce(json(401, {})) // refresh recusado

    await expect(api('/accounts')).rejects.toMatchObject({ status: 401 })

    expect(useAuth.getState().status).toBe('anonymous')
    expect(useAuth.getState().accessToken).toBeNull()
  })

  it('rotas de autenticação não tentam renovar nem mandam o token', async () => {
    fetchMock.mockResolvedValueOnce(json(401, { code: 'INVALID_CREDENTIALS', message: 'Invalid credentials' }))

    await expect(api('/auth/login', { method: 'POST', body: {}, anonymous: true })).rejects.toMatchObject({
      code: 'INVALID_CREDENTIALS',
    })

    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(fetchMock.mock.calls[0][1].headers.Authorization).toBeUndefined()
  })

  it('204 devolve undefined e queda de rede vira ApiError incerto', async () => {
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }))
    await expect(api('/x', { method: 'DELETE' })).resolves.toBeUndefined()

    fetchMock.mockRejectedValueOnce(new TypeError('Failed to fetch'))
    const error = (await api('/x').catch((e) => e)) as ApiError
    expect(error).toBeInstanceOf(ApiError)
    expect(error.network).toBe(true)
    expect(error.uncertain).toBe(true)
  })
})

describe('ApiError.uncertain', () => {
  it('é incerto em rede, 5xx, 409 e 429; definitivo nos demais 4xx', () => {
    for (const status of [500, 503, 409, 429]) expect(new ApiError(status, 'X', 'x').uncertain).toBe(true)
    for (const status of [400, 404, 422]) expect(new ApiError(status, 'X', 'x').uncertain).toBe(false)
  })
})

describe('messageFor', () => {
  it('traduz códigos da API e nunca mostra texto técnico', () => {
    expect(messageFor(new ApiError(422, 'INSUFFICIENT_FUNDS', 'Insufficient funds'))).toBe('Saldo insuficiente.')
    expect(messageFor(new ApiError(500, 'INTERNAL_ERROR', 'Internal error'))).toContain('Erro no servidor')
    expect(messageFor(new ApiError(0, 'NETWORK', 'x', undefined, true))).toContain('Sem conexão')
    expect(messageFor(new Error('boom'))).toBe('Algo deu errado. Tente de novo.')
  })
})
