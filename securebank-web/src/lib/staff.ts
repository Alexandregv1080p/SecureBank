export type StaffRole = 'ADMIN' | 'SUPPORT'

export interface StaffSection {
  to: string
  label: string
  description: string
  /** Papéis da equipe que veem a seção (o servidor confere de novo em cada chamada). */
  roles: StaffRole[]
}

export const staffSections: StaffSection[] = [
  { to: '/equipe/auditoria', label: 'Auditoria', description: 'Trilha de eventos de segurança e de operações críticas.', roles: ['ADMIN', 'SUPPORT'] },
  { to: '/equipe/clientes', label: 'Clientes', description: 'Busca por nome, e-mail, telefone ou CPF, com as contas de cada cliente.', roles: ['ADMIN', 'SUPPORT'] },
  { to: '/equipe/contas', label: 'Contas e limites', description: 'Bloquear ou desbloquear uma conta e ajustar limites.', roles: ['ADMIN'] },
  { to: '/equipe/cambio', label: 'Câmbio', description: 'Cotação comercial e spread de cada moeda.', roles: ['ADMIN'] },
  { to: '/equipe/usuarios', label: 'Equipe', description: 'Criar, desativar e reativar usuários da equipe.', roles: ['ADMIN'] },
]

export const isStaff = (roles: string[]) => roles.length > 0 && !roles.includes('CUSTOMER')

/** Seções que a pessoa vê, pelo papel dela. */
export function sectionsFor(roles: string[]): StaffSection[] {
  return staffSections.filter((s) => s.roles.some((r) => roles.includes(r)))
}

export const canAccess = (roles: string[], path: string) => sectionsFor(roles).some((s) => s.to === path)

export const roleLabel = (role: string) => ({ ADMIN: 'Administrador', SUPPORT: 'Suporte', CUSTOMER: 'Cliente' })[role] ?? role

/** Eventos da trilha de auditoria, com o nome em português (a lista segue o AuditEvent do servidor). */
export const auditEvents: Record<string, string> = {
  USER_REGISTERED: 'Cadastro',
  LOGIN_SUCCESS: 'Login',
  LOGIN_FAILED: 'Login recusado',
  LOGOUT: 'Saída',
  SESSION_REVOKED: 'Sessão encerrada',
  REFRESH_TOKEN_REUSE_DETECTED: 'Reuso de token detectado',
  PASSWORD_CHANGED: 'Senha trocada',
  MFA_ENABLED: 'Verificação em duas etapas ligada',
  MFA_DISABLED: 'Verificação em duas etapas desligada',
  MFA_FAILED: 'Código de verificação recusado',
  TRANSFER_CREATED: 'Transferência',
  TRANSFER_FAILED: 'Transferência recusada',
  PAYMENT_CREATED: 'Pagamento',
  PAYMENT_FAILED: 'Pagamento recusado',
  ACCOUNT_BLOCKED: 'Conta bloqueada',
  ACCOUNT_UNBLOCKED: 'Conta desbloqueada',
  LIMIT_CHANGED: 'Limite alterado',
  PIX_KEY_CREATED: 'Chave Pix criada',
  PIX_KEY_DELETED: 'Chave Pix removida',
  PIX_SENT: 'Pix enviado',
  PIX_FAILED: 'Pix recusado',
  PIX_REFUNDED: 'Pix devolvido',
  PIX_CHARGE_CREATED: 'Cobrança Pix criada',
  PIX_CHARGE_CANCELED: 'Cobrança Pix cancelada',
  PIX_SCHEDULED: 'Pix agendado',
  PIX_SCHEDULE_CANCELED: 'Pix agendado cancelado',
  PIX_SCHEDULE_FAILED: 'Pix agendado não realizado',
  PIGGY_CREATED: 'Porquinho criado',
  PIGGY_DEPOSIT: 'Guardado no porquinho',
  PIGGY_WITHDRAW: 'Resgate do porquinho',
  PIGGY_CLOSED: 'Porquinho fechado',
  INVESTMENT_APPLIED: 'Aplicação em renda fixa',
  INVESTMENT_REDEEMED: 'Resgate de investimento',
  FX_BOUGHT: 'Compra de moeda',
  FX_SOLD: 'Venda de moeda',
  FX_RATE_CHANGED: 'Cotação alterada',
  USER_CREATED: 'Usuário da equipe criado',
  USER_DISABLED: 'Usuário desativado',
  USER_ENABLED: 'Usuário reativado',
  CUSTOMER_SEARCHED: 'Busca de cliente',
  ACCESS_DENIED: 'Acesso negado',
}

export const auditEventLabel = (event: string) => auditEvents[event] ?? event

/** Eventos que merecem destaque na tabela (algo foi recusado ou é sensível). */
export const isAlertEvent = (event: string) =>
  /FAILED|DENIED|REUSE|BLOCKED|DISABLED|REVOKED/.test(event) && !event.endsWith('UNBLOCKED')

export const limitLabels: Record<string, string> = {
  WITHDRAW: 'Saque',
  TRANSFER: 'Transferência',
  PAYMENT: 'Pagamento',
  PIX: 'Pix',
  FX: 'Câmbio',
}

export const shortId = (id: string | null | undefined) => (id ? id.slice(0, 8) : '—')

export const isUuid = (value: string) => /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value.trim())

/** "5,2" ou "5.20" → "5.2"; só aceita número positivo com até 6 casas. */
export function parseRate(input: string): string | null {
  const text = input.trim().replace(',', '.')
  return /^\d{1,6}(\.\d{1,6})?$/.test(text) && Number(text) > 0 ? text : null
}

/** "1,5" → "1.5"; de 0 a 10 (o servidor também limita). */
export function parseSpread(input: string): string | null {
  const text = input.trim().replace(',', '.')
  return /^\d{1,2}(\.\d{1,2})?$/.test(text) && Number(text) <= 10 ? text : null
}

/** Mensagem se a busca ainda não pode ser feita (o servidor exige 3 caracteres e confere de novo); nulo = pode buscar. */
export function searchError(query: string): string | null {
  return query.trim().length < 3 ? 'Digite pelo menos 3 caracteres.' : null
}

export const accountStatusLabel = (status: string) => ({ ACTIVE: 'Ativa', BLOCKED: 'Bloqueada', CLOSED: 'Encerrada' })[status] ?? status
