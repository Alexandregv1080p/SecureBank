export interface Money {
  amount: string
  currency: string
}

export interface Page<T> {
  items: T[]
  page: number
  size: number
  totalElements: number
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
