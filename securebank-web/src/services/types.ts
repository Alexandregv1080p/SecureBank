export interface Money {
  amount: string
  currency: string
}

export interface Account {
  id: string
  branch: string
  accountNumber: string
  type: 'CHECKING' | 'SAVINGS'
  status: 'ACTIVE' | 'BLOCKED' | 'CLOSED'
  balance: Money
  createdAt: string
}

export interface Transaction {
  id: string
  type:
    | 'DEPOSIT'
    | 'WITHDRAW'
    | 'TRANSFER'
    | 'PAYMENT'
    | 'REFUND'
    | 'PIGGY_IN'
    | 'PIGGY_OUT'
    | 'PIX_OUT'
    | 'PIX_IN'
    | 'PIX_RETURN_OUT'
    | 'PIX_RETURN_IN'
    | 'INVEST_OUT'
    | 'INVEST_IN'
    | 'FX_BUY'
    | 'FX_SELL'
    | 'CARD_PURCHASE'
    | 'CARD_INVOICE_PAYMENT'
  direction: 'CREDIT' | 'DEBIT'
  amount: Money
  balanceAfter: Money
  status: string
  reference: string | null
  createdAt: string
}

export interface Page<T> {
  items: T[]
  page: number
  size: number
  totalElements: number
}

export interface LimitUsage {
  type: 'WITHDRAW' | 'TRANSFER' | 'PAYMENT' | 'PIX' | 'FX' | 'CARD'
  perOperation: Money
  daily: Money
  usedToday: Money
  remainingToday: Money
}

/** Para quem vai uma transferência, como o servidor mostra antes de confirmar (nome e CPF mascarados). */
export interface Destination {
  branch: string
  accountNumber: string
  accountType: 'CHECKING' | 'SAVINGS'
  status: string
  name: string
  document: string
  bank: string
  ownAccount: boolean
}

export interface Transfer {
  id: string
  sourceAccountId: string
  destinationAccountId: string
  amount: Money
  description: string | null
  status: string
  createdAt: string
}

export interface Payment {
  id: string
  accountId: string
  amount: Money
  barcode: string
  description: string | null
  status: string
  createdAt: string
}

export interface Notification {
  id: string
  type: string
  title: string
  body: string
  createdAt: string
  read: boolean
}

export interface SessionInfo {
  id: string
  createdAt: string
  lastUsedAt: string
  expiresAt: string
  ip: string | null
  userAgent: string | null
  mfaVerified: boolean
  current: boolean
}

export interface Customer {
  id: string
  name: string
  document: string
  email: string
  phone: string
}

export interface CategoryTotal {
  category: string
  income: Money
  expenses: Money
}

/** Resumo do mês de uma conta (só lançamentos concluídos). */
export interface StatementSummary {
  month: string
  income: Money
  expenses: Money
  net: Money
  byCategory: CategoryTotal[]
}

export interface InvestmentProduct {
  code: string
  name: string
  kind: 'DAILY' | 'TERM'
  annualRatePercent: string
  termDays: number | null
  minAmount: string
}

/** Aplicação. Bruto, rendimento, IR e líquido são "se resgatar agora"; depois do resgate, o que foi pago. */
export interface Investment {
  id: string
  accountId: string
  productCode: string
  productName: string
  principal: Money
  annualRatePercent: string
  termDays: number | null
  appliedAt: string
  maturesAt: string | null
  status: 'ACTIVE' | 'REDEEMED'
  daysHeld: number
  gross: Money
  yield: Money
  tax: Money
  taxRatePercent: string
  net: Money
  canRedeem: boolean
  redeemedAt: string | null
  /** Quanto caiu na conta neste resgate (só na resposta do resgate). */
  paidAmount?: Money | null
}

export interface Piggy {
  id: string
  accountId: string
  name: string
  balance: Money
  goal: Money | null
  progressPercent: number | null
  goalReached: boolean
  status: 'ACTIVE' | 'CLOSED'
  createdAt: string
}

export interface FxWallet {
  currency: string
  balance: Money
}

export type CardStatus = 'ACTIVE' | 'BLOCKED' | 'CANCELED'

/** Cartão de débito virtual: a listagem só traz os 4 últimos dígitos; o número inteiro vem de /reveal. */
export type CardKind = 'DEBIT' | 'CREDIT'

export interface Card {
  id: string
  accountId: string
  kind: CardKind
  holderName: string
  maskedNumber: string
  last4: string
  expiryMonth: number
  expiryYear: number
  status: CardStatus
  /** Por que foi bloqueado ou cancelado, nas palavras do cliente. */
  statusReason?: string | null
  createdAt: string
}

export interface CardDetails {
  number: string
  expiryMonth: number
  expiryYear: number
  cvv: string
  holderName: string
}

export interface CardPurchase {
  id: string
  cardId: string
  amount: Money
  merchant: string
  transactionId: string
  createdAt: string
}

export type InvoiceStatus = 'OPEN' | 'CLOSED' | 'OVERDUE' | 'REVOLVING' | 'PAID'

/** Fatura de um mês (AAAA-MM): vence no dia escolhido e fecha 7 dias antes. */
export interface Invoice {
  month: string
  closingDate: string
  dueDate: string
  principal: Money
  /** Multa e juros por atraso. */
  charges: Money
  total: Money
  paid: Money
  remaining: Money
  /** Quanto falta pagar para cumprir o pagamento mínimo (zero se já cumpriu). */
  minimumDue: Money
  status: InvoiceStatus
}

export interface CreditSummary {
  cardId: string
  creditLimit: Money
  used: Money
  available: Money
  dueDay: number
  overdue: boolean
  currentInvoice: Invoice
  /** Preenchido num cartão adicional: o principal, cujo limite e faturas ele usa. */
  mainCardId: string | null
}

export interface InvoiceItem {
  merchant: string
  number: number
  installments: number
  amount: Money
  purchasedOn: string
}

export interface InvoiceDetail {
  invoice: Invoice
  items: InvoiceItem[]
  charges: { kind: 'LATE_FEE' | 'INTEREST'; amount: Money }[]
  payments: { amount: Money; createdAt: string; kind: 'CASH' | 'REFINANCE' }[]
}

export interface RefinancePlan {
  installments: number
  installment: Money
  total: Money
  interest: Money
  /** Juros ao mês em %, ex.: "5". */
  monthlyRate: string
}

export interface RefinanceOffer {
  invoice: Invoice
  options: RefinancePlan[]
}

export interface CreditPurchase {
  id: string
  cardId: string
  /** O cartão adicional que fez a compra (nulo = o principal). */
  madeWith: string | null
  kind: 'PURCHASE' | 'REFINANCE'
  merchant: string
  total: Money
  installments: number
  installmentAmount: Money
  createdAt: string
}
