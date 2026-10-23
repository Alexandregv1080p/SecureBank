export interface Claims {
  sub: string
  cid?: string
  roles: string[]
  sid: string
  exp: number
}

/** Lê os claims só para a interface (nome do papel, expiração). Quem valida o token é sempre o servidor. */
export function decodeClaims(token: string): Claims {
  const payload = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')
  return JSON.parse(atob(payload)) as Claims
}
