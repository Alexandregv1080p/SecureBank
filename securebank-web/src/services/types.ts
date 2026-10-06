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
  type: 'DEPOSIT' | 'WITHDRAW' | 'TRANSFER' | 'PAYMENT' | 'REFUND' | 'PIGGY_IN' | 'PIGGY_OUT'
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
  type: 'WITHDRAW' | 'TRANSFER' | 'PAYMENT'
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
