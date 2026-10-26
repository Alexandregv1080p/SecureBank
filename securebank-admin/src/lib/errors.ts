import { ApiError } from './api'

const messages: Record<string, string> = {
  INVALID_CREDENTIALS: 'E-mail ou senha incorretos.',
  TOO_MANY_ATTEMPTS: 'Muitas tentativas. Aguarde alguns minutos e tente de novo.',
  RATE_LIMITED: 'Muitas requisições. Aguarde um instante.',
  INVALID_MFA_CODE: 'Código inválido ou já utilizado.',
  INVALID_MFA_CHALLENGE: 'A verificação expirou. Entre novamente.',
  CUSTOMER_ALREADY_EXISTS: 'Já existe um cadastro com esses dados.',
  INSUFFICIENT_FUNDS: 'Saldo insuficiente.',
  DEPOSIT_LIMIT_EXCEEDED: 'Um depósito é de até R$ 100.000,00 por vez.',
  LIMIT_EXCEEDED: 'Este valor ultrapassa o seu limite.',
  CARD_NOT_USABLE: 'Este cartão não aceita compras agora: está bloqueado, cancelado ou vencido.',
  CREDIT_LIMIT_EXCEEDED: 'Esta compra passa do limite disponível do cartão.',
  CREDIT_OVERDUE: 'Há fatura vencida: pague-a antes de comprar no crédito.',
  INVOICE_NOT_PAYABLE: 'Esta fatura não aceita esse pagamento: confira o valor que falta.',
  CARD_HAS_DEBT: 'Este cartão ainda tem fatura em aberto. Pague tudo antes de cancelar.',
  CARD_IS_CREDIT: 'Este é um cartão de crédito: a compra segue pelo crédito.',
  CARD_LIMIT_REACHED: 'Esta conta já tem 3 cartões. Cancele um para pedir outro.',
  ACCOUNT_NOT_ACTIVE: 'A conta não está ativa para esta operação.',
  ACCOUNT_LIMIT_REACHED: 'Você já tem 5 contas abertas. Encerre uma para abrir outra.',
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
  INVESTMENT_BELOW_MINIMUM: 'O valor está abaixo do mínimo deste produto.',
  INVESTMENT_NOT_MATURED: 'Este investimento só pode ser resgatado no vencimento.',
  INVESTMENT_ALREADY_REDEEMED: 'Este investimento já foi resgatado.',
  INVESTMENT_LIMIT_REACHED: 'Você já tem o máximo de 50 investimentos ativos.',
  FX_RATE_CHANGED: 'A cotação mudou. Confira o novo valor e tente de novo.',
  INSUFFICIENT_FX_FUNDS: 'Saldo insuficiente na carteira em moeda estrangeira.',
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
  MFA_REQUIRED: 'Ligue a verificação em duas etapas e entre de novo para usar o painel.',
  APPROVAL_ALREADY_PENDING: 'Já existe um pedido aguardando decisão para este item.',
  APPROVAL_NOT_PENDING: 'Este pedido já foi decidido.',
  CANNOT_DECIDE_OWN_REQUEST: 'Você não pode decidir um pedido que você mesmo fez.',
  ACCOUNT_NOT_BLOCKED: 'Esta conta não está bloqueada.',
  CANNOT_CHANGE_SELF: 'Você não pode mudar a sua própria posição na equipe.',
  FORBIDDEN: 'O seu perfil não tem permissão para isto.',
}

/** O servidor recusa a senha com INVALID_VALUE e um texto em inglês: aqui vira o motivo, em português. */
function passwordMessage(text: string): string {
  if (text.includes('too easy')) return 'Essa senha é fácil de adivinhar. Escolha outra, mais longa e menos comum.'
  if (text.includes('email')) return 'A senha não pode conter o e-mail.'
  return 'A senha precisa ter de 12 a 128 caracteres.'
}

export function messageFor(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.network) return 'Sem conexão com o servidor. Tente de novo.'
    if (error.code === 'INVALID_VALUE' && error.message.startsWith('Password')) return passwordMessage(error.message)
    return messages[error.code] ?? (error.status >= 500 ? 'Erro no servidor. Tente de novo em instantes.' : error.message)
  }
  return 'Algo deu errado. Tente de novo.'
}
