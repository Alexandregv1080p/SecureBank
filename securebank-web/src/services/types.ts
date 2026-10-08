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
  type: 'WITHDRAW' | 'TRANSFER' | 'PAYMENT' | 'PIX' | 'FX'
  perOperation: Money
  daily: Money
  usedToday: Money
  remainingToday: Money
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

export interface Investment {
  id: string
  productName: string
  principal: Money
  net: Money
  yield: Money
  status: 'ACTIVE' | 'REDEEMED'
  annualRatePercent: string
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
