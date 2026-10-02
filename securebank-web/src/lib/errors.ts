import { ApiError } from './api'

const messages: Record<string, string> = {
  INVALID_CREDENTIALS: 'E-mail ou senha incorretos.',
  TOO_MANY_ATTEMPTS: 'Muitas tentativas. Aguarde alguns minutos e tente de novo.',
  RATE_LIMITED: 'Muitas requisições. Aguarde um instante.',
  INVALID_MFA_CODE: 'Código inválido ou já utilizado.',
  INVALID_MFA_CHALLENGE: 'A verificação expirou. Entre novamente.',
  CUSTOMER_ALREADY_EXISTS: 'Já existe um cadastro com esses dados.',
  INSUFFICIENT_FUNDS: 'Saldo insuficiente.',
  LIMIT_EXCEEDED: 'Este valor ultrapassa o seu limite.',
  ACCOUNT_NOT_ACTIVE: 'A conta não está ativa para esta operação.',
  CUSTOMER_NOT_ACTIVE: 'Seu cadastro não está ativo. Fale com o suporte.',
  NOT_FOUND: 'Não encontramos o que você procurou. Confira os dados.',
  CURRENCY_MISMATCH: 'As contas usam moedas diferentes.',
  INVALID_CURRENT_PASSWORD: 'A senha atual está incorreta.',
  MFA_ALREADY_ENABLED: 'A verificação em duas etapas já está ativa.',
  IDEMPOTENCY_KEY_IN_PROGRESS: 'Esta operação ainda está sendo processada. Aguarde.',
  CONCURRENT_UPDATE: 'A conta foi alterada por outra operação. Tente novamente.',
  VALIDATION_ERROR: 'Confira os campos informados.',
  INVALID_VALUE: 'Algum dado informado é inválido.',
}

export function messageFor(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.network) return 'Sem conexão com o servidor. Tente de novo.'
    return messages[error.code] ?? (error.status >= 500 ? 'Erro no servidor. Tente de novo em instantes.' : error.message)
  }
  return 'Algo deu errado. Tente de novo.'
}
