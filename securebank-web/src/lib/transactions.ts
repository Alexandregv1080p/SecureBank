import type { Transaction } from '../services/types'

/** Nome do lançamento para o cliente (o mesmo do app Android). */
export function describeTransaction(t: Pick<Transaction, 'type' | 'direction'>): string {
  switch (t.type) {
    case 'DEPOSIT':
      return 'Depósito'
    case 'WITHDRAW':
      return 'Saque'
    case 'TRANSFER':
      return t.direction === 'CREDIT' ? 'Transferência recebida' : 'Transferência enviada'
    case 'PAYMENT':
      return 'Pagamento'
    case 'PIX_OUT':
      return 'Pix enviado'
    case 'PIX_IN':
      return 'Pix recebido'
    case 'PIX_RETURN_OUT':
      return 'Devolução de Pix enviada'
    case 'PIX_RETURN_IN':
      return 'Devolução de Pix recebida'
    case 'PIGGY_IN':
      return 'Guardado no porquinho'
    case 'PIGGY_OUT':
      return 'Resgate do porquinho'
    case 'INVEST_OUT':
      return 'Aplicação em renda fixa'
    case 'INVEST_IN':
      return 'Resgate de investimento'
    case 'FX_BUY':
      return 'Compra de moeda'
    case 'FX_SELL':
      return 'Venda de moeda'
    default:
      return 'Estorno'
  }
}
