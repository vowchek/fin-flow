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
  reinvestedIncomeAbs?: number | null
  currency: string | null
  priceAsOf: string | null
  createdAt: string
  updatedAt: string
  expectedIncomeAbs?: number | null
  expectedIncomeBasis?: string | null
}

export type HoldingDetail = {
  holdingId: string
  symbol: string
  name: string | null
  logoUrl: string | null
  cash: boolean
  quantity: number
  unitPrice: number | null
  dayChangeAbs: number | null
  dayChangePct: number | null
  marketValue: number | null
  currency: string | null
  priceHistory: ValuePoint[]
  dividendThisYearPerUnit?: number | null
  dividendThisYearBasis?: string | null
  dividendNextYearPerUnit?: number | null
  dividendNextYearBasis?: string | null
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
  investedAmount?: number | null
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
  /** ACTUAL | FORECAST | NONE */
  basis?: string | null
  /** Median YoY growth % used for forecast */
  growthPct?: number | null
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
  /** Default true: new money. False: buy from dividends — do not increase invested. */
  addToInvested?: boolean
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
  paysDividends?: boolean | null
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
  id: string
  name: string
  displayOrder: number
}

export type Expense = {
  id: string
  categoryId: string
  categoryName: string
  amount: number
  currency: string
  yearMonth: string
  note: string | null
  createdAt: string
  updatedAt: string
}

export type ExpenseSummary = {
  byCategoryByMonth: Record<string, Record<string, number>>
  totalsByCategory: Record<string, number>
  total: number
}

export type PortfolioKind = 'stock' | 'crypto'

export type Page<T> = {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}
