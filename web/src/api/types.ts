export type User = {
  id: string
  email: string
  displayName: string | null
  role: 'USER' | 'ADMIN'
}

export type AuthResponse = {
  accessToken: string
  tokenType: string
  expiresInSeconds: number
  user: User
}

export type Holding = {
  id: string
  symbol: string
  name: string | null
  quantity: number
  openedOn: string | null
  logoUrl: string | null
  unitPrice: number | null
  marketValue: number | null
  costBasis: number | null
  dayChangeAbs: number | null
  dayChangePct: number | null
  totalChangeAbs: number | null
  totalChangePct: number | null
  currency: string | null
  priceAsOf: string | null
  createdAt: string
  updatedAt: string
}

export type ValuePoint = {
  date: string
  value: number
}

export type PortfolioSummary = {
  id: string
  name: string
  description: string | null
  holdingsCount: number
  holdings: Holding[]
  totalValue: number | null
  dayChangeAbs: number | null
  dayChangePct: number | null
  totalChangeAbs: number | null
  totalChangePct: number | null
  currency: string | null
  createdAt: string
  updatedAt: string
}

export type PortfolioDetail = {
  id: string
  name: string
  description: string | null
  holdings: Holding[]
  valueHistory: ValuePoint[]
  totalValue: number | null
  dayChangeAbs: number | null
  dayChangePct: number | null
  totalChangeAbs: number | null
  totalChangePct: number | null
  currency: string | null
  createdAt: string
  updatedAt: string
}

export type TradeSide = 'BUY' | 'SELL'

export type Trade = {
  id: string
  side: TradeSide
  quantity: number
  occurredOn: string
  unitPrice: number | null
  note: string | null
  createdAt: string
}

export type TradePayload = {
  quantity: number
  occurredOn?: string
  unitPrice?: number
  note?: string
}

export type Instrument = {
  id: string
  market: 'MOEX' | 'CRYPTO'
  symbol: string
  externalId: string
  name: string
  logoUrl: string | null
  currency: string
  enabled: boolean
}

export type ExpenseCategory = {
  code: string
  label: string
}

export type Expense = {
  id: string
  category: string
  amount: number
  currency: string
  yearMonth: string
  note: string | null
  createdAt: string
  updatedAt: string
}

export type PortfolioKind = 'stock' | 'crypto'
