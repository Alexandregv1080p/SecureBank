package com.securebank.mobile.core.network

private val messages = mapOf(
    "INVALID_CREDENTIALS" to "E-mail ou senha incorretos.",
    "TOO_MANY_ATTEMPTS" to "Muitas tentativas. Aguarde alguns minutos e tente de novo.",
    "RATE_LIMITED" to "Muitas requisições. Aguarde um instante.",
    "INVALID_MFA_CODE" to "Código inválido ou já utilizado.",
    "INVALID_MFA_CHALLENGE" to "A verificação expirou. Entre novamente.",
    "CUSTOMER_ALREADY_EXISTS" to "Já existe um cadastro com esses dados.",
    "INSUFFICIENT_FUNDS" to "Saldo insuficiente.",
    "LIMIT_EXCEEDED" to "Este valor ultrapassa o seu limite.",
    "ACCOUNT_NOT_ACTIVE" to "A conta não está ativa para esta operação.",
    "CUSTOMER_NOT_ACTIVE" to "Seu cadastro não está ativo. Fale com o suporte.",
    "NOT_FOUND" to "Não encontramos o que você procurou. Confira os dados.",
    "CURRENCY_MISMATCH" to "As contas usam moedas diferentes.",
    "INVALID_CURRENT_PASSWORD" to "A senha atual está incorreta.",
    "MFA_ALREADY_ENABLED" to "A verificação em duas etapas já está ativa.",
    "IDEMPOTENCY_KEY_IN_PROGRESS" to "Esta operação ainda está sendo processada. Aguarde.",
    "CONCURRENT_UPDATE" to "A conta foi alterada por outra operação. Tente novamente.",
    "VALIDATION_ERROR" to "Confira os campos informados.",
    "INVALID_VALUE" to "Algum dado informado é inválido.",
    "NOT_A_CUSTOMER" to "Este aplicativo é para clientes. Contas da equipe usam o painel web.",
    "PIX_KEY_IN_USE" to "Esta chave já está cadastrada.",
    "PIX_KEY_LIMIT_REACHED" to "Você já tem o máximo de 5 chaves Pix.",
    "INSUFFICIENT_PIGGY_FUNDS" to "Saldo insuficiente no porquinho.",
    "PIGGY_CLOSED" to "Este porquinho está fechado.",
    "PIGGY_HAS_BALANCE" to "Resgate o dinheiro do porquinho antes de fechar.",
    "PIGGY_LIMIT_REACHED" to "Você já tem o máximo de 20 porquinhos.",
    "SERVICE_UNAVAILABLE" to "Serviço temporariamente indisponível. Tente de novo em instantes.",
)

/** Texto para o usuário: nunca mostra mensagem técnica do servidor (só o mapa de códigos conhecidos). */
fun messageFor(error: Throwable): String {
    if (error !is ApiError) return "Algo deu errado. Tente de novo."
    if (error.network) return "Sem conexão com o servidor. Tente de novo."
    return messages[error.code]
        ?: if (error.status >= 500) "Erro no servidor. Tente de novo em instantes." else "Não foi possível concluir. Tente de novo."
}
