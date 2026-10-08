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
  INSUFFICIENT_PIGGY_FUNDS: 'Saldo insuficiente no porquinho.',
  PIGGY_CLOSED: 'Este porquinho está fechado.',
  PIGGY_HAS_BALANCE: 'Resgate o dinheiro do porquinho antes de fechar.',
  PIGGY_LIMIT_REACHED: 'Você já tem o máximo de 20 porquinhos.',
  PIX_KEY_IN_USE: 'Esta chave já está cadastrada.',
  PIX_KEY_LIMIT_REACHED: 'Você já tem o máximo de 5 chaves Pix.',
  PIX_REFUND_EXPIRED: 'O prazo de 90 dias para devolver este Pix acabou.',
  PIX_REFUND_EXCEEDS: 'O valor passa do que ainda pode ser devolvido.',
  PIX_NOT_REFUNDABLE: 'Este Pix não pode ser devolvido.',
  PIX_CHARGE_EXPIRED: 'Esta cobrança expirou.',
  PIX_CHARGE_NOT_PAYABLE: 'Esta cobrança não pode mais ser paga (já foi paga ou cancelada).',
  PIX_CHARGE_NOT_CANCELABLE: 'Esta cobrança não pode mais ser cancelada.',
  PIX_SCHEDULE_LIMIT_REACHED: 'Você já tem o máximo de 20 Pix agendados.',
  PIX_SCHEDULE_NOT_CANCELABLE: 'Este agendamento não pode mais ser cancelado.',
  FX_RATE_CHANGE_TOO_LARGE: 'A cotação não pode mudar mais de 20% de uma vez. Faça o ajuste em etapas.',
  FX_SPREAD_TOO_LARGE: 'O spread não pode passar de 10%.',
  USER_ALREADY_EXISTS: 'Já existe um usuário com este e-mail.',
  CANNOT_DISABLE_SELF: 'Você não pode desativar o seu próprio usuário.',
  WEAK_PASSWORD: 'A senha não atende à política de segurança.',
}

export function messageFor(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.network) return 'Sem conexão com o servidor. Tente de novo.'
    return messages[error.code] ?? (error.status >= 500 ? 'Erro no servidor. Tente de novo em instantes.' : error.message)
  }
  return 'Algo deu errado. Tente de novo.'
}
