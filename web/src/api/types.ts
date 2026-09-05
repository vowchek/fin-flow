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
  cash: boolean
  logoUrl: string | null
  unitPrice: number | null
  marketValue: number | null
  costBasis: number | null
  dayChangeAbs: number | null
  dayChangePct: number | null
  totalChangeAbs: number | null
  totalChangePct: number | null
  incomeAbs: number | null
  currency: string | null
  priceAsOf: string | null
  createdAt: string
  updatedAt: string
}

export type ValuePoint = {
  date: string
  value: number
  invested: number | null
}

export type PortfolioEntryMode = 'LAZY' | 'MANUAL' | 'BROKER_REPORT' | 'BROKER_API'

export type PortfolioSummary = {
  id: string
  name: string
  description: string | null
  entryMode: PortfolioEntryMode
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
  entryMode: PortfolioEntryMode
  holdings: Holding[]
  valueHistory: ValuePoint[]
  totalValue: number | null
  dayChangeAbs: number | null
  dayChangePct: number | null
  totalChangeAbs: number | null
  totalChangePct: number | null
  currency: string | null
  taxRatePercent?: number | null
  createdAt: string
  updatedAt: string
}

export type AssetKind = 'EQUITY' | 'BOND'

export type HoldingCashflow = {
  holdingId: string
  symbol: string
  assetKind: AssetKind
  annualCashflowPerUnit: number | null
  cashflowGrowthPct: number | null
  cashflowUntilYear: number | null
}

export type PassiveIncome = {
  year: number
  taxRatePercent: number
  annualGross: number
  annualNet: number
  monthlyNet: number
  currency: string
}

export type MarketStripItem = {
  id: string
  label: string
  value: number
  changePct: number | null
  displayPrefix: string
  displaySuffix: string
}

export type TradeSide = 'BUY' | 'SELL'

export type TxKind = 'TRADE' | 'DEPOSIT' | 'WITHDRAW' | 'DIVIDEND' | 'COUPON'

export type Trade = {
  id: string
  side: TradeSide
  kind?: TxKind | null
  quantity: number
  occurredOn: string
  unitPrice: number | null
  note: string | null
  settleToCash?: boolean
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
  assetKind?: AssetKind | null
  annualCashflowPerUnit?: number | null
  cashflowGrowthPct?: number | null
  cashflowUntilYear?: number | null
}

export type PaymentCalendarItem = {
  symbol: string
  name: string
  kind: string
  date: string
  perUnit: number
  quantity: number
  amount: number
  currency: string
}

export type PaymentCalendar = {
  year: number
  items: PaymentCalendarItem[]
  totalAmount: number
  currency: string
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
