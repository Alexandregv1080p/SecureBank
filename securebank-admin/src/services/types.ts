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
