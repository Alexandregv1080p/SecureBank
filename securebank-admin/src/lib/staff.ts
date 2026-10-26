import { formatBRL, parseAmount } from './money'
import { amountIssue } from './validation'

export type StaffRole = 'ADMIN' | 'SUPPORT' | 'RISK' | 'CREDIT' | 'FINANCE' | 'AUDITOR'
export type Department = 'ATENDIMENTO' | 'RISCO' | 'CREDITO' | 'FINANCEIRO' | 'AUDITORIA' | 'DIRETORIA'
export type StaffLevel = 'ANALYST' | 'MANAGER'
export type Permission =
  | 'VIEW_CUSTOMER'
  | 'VIEW_AUDIT'
  | 'MANAGE_USERS'
  | 'MANAGE_LIMITS'
  | 'MANAGE_ACCOUNTS'
  | 'MANAGE_FX_RATES'
  | 'VIEW_REPORTS'
  | 'DECIDE_APPROVALS'

/** Espelho do que cada papel pode no servidor (Role.java). A tela só decide o que mostrar: o servidor confere em toda chamada. */
const rolePermissions: Record<StaffRole, Permission[]> = {
  SUPPORT: ['VIEW_CUSTOMER', 'VIEW_AUDIT'],
  RISK: ['VIEW_CUSTOMER', 'VIEW_AUDIT', 'MANAGE_ACCOUNTS', 'DECIDE_APPROVALS'],
  CREDIT: ['VIEW_CUSTOMER', 'MANAGE_LIMITS', 'DECIDE_APPROVALS'],
  FINANCE: ['VIEW_REPORTS', 'MANAGE_FX_RATES'],
  AUDITOR: ['VIEW_CUSTOMER', 'VIEW_AUDIT', 'VIEW_REPORTS'],
  ADMIN: ['VIEW_CUSTOMER', 'VIEW_AUDIT', 'MANAGE_USERS', 'MANAGE_LIMITS', 'MANAGE_ACCOUNTS', 'MANAGE_FX_RATES', 'VIEW_REPORTS', 'DECIDE_APPROVALS'],
}

export const can = (roles: string[], permission: Permission) => roles.some((r) => rolePermissions[r as StaffRole]?.includes(permission))

export interface DepartmentInfo {
  code: Department
  label: string
  role: StaffRole
  /** O que o departamento faz, em uma frase. */
  summary: string
  /** O que muda entre analista e gerente. */
  hierarchy: string
}

/** Departamentos da equipe: o papel (e as permissões) vem do departamento; o nível separa quem pede de quem decide. */
export const departments: DepartmentInfo[] = [
  { code: 'ATENDIMENTO', label: 'Atendimento', role: 'SUPPORT', summary: 'Consulta clientes e a auditoria, sem alterar nada.', hierarchy: 'Sem ações que precisem de alçada: analista e gerente consultam.' },
  { code: 'RISCO', label: 'Risco e fraude', role: 'RISK', summary: 'Bloqueia contas e cartões e vê a auditoria.', hierarchy: 'Analista bloqueia na hora; desbloquear conta precisa de um gerente de Risco.' },
  { code: 'CREDITO', label: 'Crédito', role: 'CREDIT', summary: 'Decide limites de cartão e de operação.', hierarchy: 'Analista decide limite de cartão até R$ 20.000,00 (e sempre pode reduzir); acima disso um gerente de Crédito aprova.' },
  { code: 'FINANCEIRO', label: 'Financeiro', role: 'FINANCE', summary: 'Ajusta cotação e spread do câmbio e acompanha o painel gerencial.', hierarchy: 'Analista e gerente têm o mesmo acesso.' },
  { code: 'AUDITORIA', label: 'Auditoria', role: 'AUDITOR', summary: 'Só leitura: clientes, auditoria e painel gerencial.', hierarchy: 'Ninguém altera nada neste departamento.' },
  { code: 'DIRETORIA', label: 'Diretoria', role: 'ADMIN', summary: 'Administra tudo, inclusive a equipe.', hierarchy: 'Só gerentes. Age sem pedir e aprova qualquer pedido, menos os próprios.' },
]

export const departmentLabel = (code: string) => departments.find((d) => d.code === code)?.label ?? code
export const levelLabel = (level: string) => ({ ANALYST: 'Analista', MANAGER: 'Gerente' })[level] ?? level

/** "Gerente de Crédito", "Analista de Risco e fraude" ou, na Diretoria, só "Diretoria". */
export function positionLabel(department: string | undefined, level: string | undefined): string | null {
  if (!department || !level) return null
  if (department === 'DIRETORIA') return 'Diretoria'
  return `${levelLabel(level)} · ${departmentLabel(department)}`
}

export interface StaffSection {
  to: string
  label: string
  description: string
  /** Quem tem pelo menos uma destas permissões vê a seção (o servidor confere de novo em cada chamada). */
  permissions: Permission[]
  /** Toda a equipe vê (segurança da própria conta). */
  everyone?: boolean
}

export const staffSections: StaffSection[] = [
  { to: '/', label: 'Visão geral', description: 'Clientes, saldos, movimento, crédito e sinais de segurança do banco.', permissions: ['VIEW_REPORTS'] },
  { to: '/aprovacoes', label: 'Aprovações', description: 'Pedidos que passaram da alçada de quem pediu e esperam um gerente.', permissions: ['DECIDE_APPROVALS'] },
  { to: '/auditoria', label: 'Auditoria', description: 'Trilha de eventos de segurança e de operações críticas.', permissions: ['VIEW_AUDIT'] },
  { to: '/clientes', label: 'Clientes', description: 'Busca por nome, e-mail, telefone ou CPF, com as contas de cada cliente.', permissions: ['VIEW_CUSTOMER'] },
  { to: '/contas', label: 'Contas e limites', description: 'Bloquear ou desbloquear uma conta e ajustar limites.', permissions: ['MANAGE_ACCOUNTS', 'MANAGE_LIMITS'] },
  { to: '/cambio', label: 'Câmbio', description: 'Cotação comercial e spread de cada moeda.', permissions: ['MANAGE_FX_RATES'] },
  { to: '/usuarios', label: 'Equipe', description: 'Departamentos, níveis e acesso de cada pessoa da equipe.', permissions: ['MANAGE_USERS'] },
  { to: '/seguranca', label: 'Minha segurança', description: 'Verificação em duas etapas, senha e dispositivos conectados.', permissions: [], everyone: true },
]

export const isStaff = (roles: string[]) => roles.length > 0 && !roles.includes('CUSTOMER')

/** Seções que a pessoa vê, pelas permissões do papel dela. */
export function sectionsFor(roles: string[]): StaffSection[] {
  if (!isStaff(roles)) return []
  return staffSections.filter((s) => s.everyone || s.permissions.some((p) => can(roles, p)))
}

export const canAccess = (roles: string[], path: string) => sectionsFor(roles).some((s) => s.to === path)

export const roleLabel = (role: string) =>
  ({ ADMIN: 'Administrador', SUPPORT: 'Atendimento', RISK: 'Risco e fraude', CREDIT: 'Crédito', FINANCE: 'Financeiro', AUDITOR: 'Auditoria', CUSTOMER: 'Cliente' })[role] ?? role

/** Pedidos que esperam um gerente. */
export const approvalKindLabel = (kind: string) => ({ CREDIT_LIMIT: 'Limite de cartão', ACCOUNT_UNBLOCK: 'Desbloqueio de conta' })[kind] ?? kind
export const approvalStatusLabel = (status: string) =>
  ({ PENDING: 'Aguardando', APPROVED: 'Aprovado', REJECTED: 'Recusado', CANCELED: 'Cancelado' })[status] ?? status

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
  CARD_ISSUED: 'Cartão emitido',
  CARD_REVEALED: 'Número do cartão consultado',
  CARD_BLOCKED: 'Cartão bloqueado',
  CARD_UNBLOCKED: 'Cartão desbloqueado',
  CARD_CANCELED: 'Cartão cancelado',
  CARD_PURCHASE: 'Compra no débito',
  CARD_PURCHASE_FAILED: 'Compra no débito recusada',
  CREDIT_PURCHASE: 'Compra no crédito',
  CREDIT_PURCHASE_FAILED: 'Operação de crédito recusada',
  CREDIT_INVOICE_PAID: 'Fatura paga',
  CREDIT_INVOICE_REFINANCED: 'Fatura parcelada',
  CREDIT_LIMIT_CHANGED: 'Limite de crédito alterado',
  INVESTMENT_APPLIED: 'Aplicação em renda fixa',
  INVESTMENT_REDEEMED: 'Resgate de investimento',
  FX_BOUGHT: 'Compra de moeda',
  FX_SOLD: 'Venda de moeda',
  FX_RATE_CHANGED: 'Cotação alterada',
  USER_CREATED: 'Usuário da equipe criado',
  USER_DISABLED: 'Usuário desativado',
  USER_ENABLED: 'Usuário reativado',
  USER_ASSIGNED: 'Posição na equipe alterada',
  APPROVAL_REQUESTED: 'Pedido de aprovação',
  APPROVAL_APPROVED: 'Pedido aprovado',
  APPROVAL_REJECTED: 'Pedido recusado',
  APPROVAL_CANCELED: 'Pedido cancelado',
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
  CARD: 'Cartão de débito',
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

export const cardStatusLabel = (status: string) => ({ ACTIVE: 'Ativo', BLOCKED: 'Bloqueado', CANCELED: 'Cancelado' })[status] ?? status

/** Limite de crédito decidido pela equipe: múltiplo de R$ 100, de R$ 100 a R$ 100.000 e nunca abaixo do que o cliente já deve. */
export function creditLimitIssue(input: string, used: string): string | null {
  const parsed = parseAmount(input)
  if (parsed === null) return amountIssue(input)
  const cents = Math.round(Number(parsed) * 100)
  if (cents % 10000 !== 0) return 'Use um valor múltiplo de R$ 100,00'
  if (cents < 10000) return 'O limite mínimo é R$ 100,00'
  if (cents > 10_000_000) return 'O limite máximo é R$ 100.000,00'
  if (cents < Math.round(Number(used) * 100)) return `Abaixo do que o cliente já usa (${formatBRL(used)})`
  return null
}

/** Motivo do bloqueio: de 3 a 140 caracteres, numa linha só (igual ao servidor). */
export function reasonIssue(reason: string): string | null {
  const t = reason.trim()
  if (t.length < 3) return 'Conte o motivo, com pelo menos 3 caracteres'
  if (t.length > 140) return 'No máximo 140 caracteres'
  if (/[\r\n]/.test(t)) return 'Escreva o motivo em uma linha só'
  return null
}
