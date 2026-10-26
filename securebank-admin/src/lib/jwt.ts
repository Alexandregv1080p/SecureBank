export interface Claims {
  sub: string
  cid?: string
  roles: string[]
  /** Só na equipe: departamento e nível (rótulos; quem decide é o papel, no servidor). */
  dept?: string
  lvl?: string
  /** Fatores usados no login: "otp" = passou pela verificação em duas etapas. */
  amr?: string[]
  sid: string
  exp: number
}

/** Lê os claims só para a interface (nome do papel, expiração). Quem valida o token é sempre o servidor. */
export function decodeClaims(token: string): Claims {
  const payload = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')
  return JSON.parse(atob(payload)) as Claims
}
