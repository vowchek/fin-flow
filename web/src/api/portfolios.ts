import { api } from './client'
import type {
  Holding,
  HoldingCashflow,
  HoldingDetail,
  Page,
  PassiveIncome,
  PaymentCalendar,
  PortfolioDetail,
  PortfolioKind,
  PortfolioSummary,
  Trade,
  TradePayload,
  ValuePoint,
} from './types'

function base(kind: PortfolioKind) {
  return kind === 'stock' ? '/api/v1/stock-portfolios' : '/api/v1/crypto-portfolios'
}

export function listPortfolios(kind: PortfolioKind) {
  return api<PortfolioSummary[]>(base(kind))
}

export function getPortfolio(kind: PortfolioKind, id: string) {
  return api<PortfolioDetail>(`${base(kind)}/${id}`)
}

export function getValueHistory(kind: PortfolioKind, id: string) {
  return api<ValuePoint[]>(`${base(kind)}/${id}/value-history`)
}

export function getHoldingDetail(kind: PortfolioKind, portfolioId: string, holdingId: string) {
  return api<HoldingDetail>(`${base(kind)}/${portfolioId}/holdings/${holdingId}/detail`)
}

export function createPortfolio(
  kind: PortfolioKind,
  name: string,
  description?: string,
  entryMode?: string,
) {
  return api<PortfolioDetail>(base(kind), {
    method: 'POST',
    body: JSON.stringify({
      name,
      description: description || null,
      entryMode: entryMode || null,
    }),
  })
}

export function deletePortfolio(kind: PortfolioKind, id: string) {
  return api<void>(`${base(kind)}/${id}`, { method: 'DELETE' })
}

export function deleteHolding(kind: PortfolioKind, portfolioId: string, holdingId: string) {
  return api<void>(`${base(kind)}/${portfolioId}/holdings/${holdingId}`, { method: 'DELETE' })
}

export function updatePortfolio(
  kind: PortfolioKind,
  id: string,
  payload: { name: string; description?: string | null; investedAmount?: number | null },
) {
  return api<PortfolioDetail>(`${base(kind)}/${id}`, {
    method: 'PUT',
    body: JSON.stringify({
      name: payload.name,
      description: payload.description ?? null,
      entryMode: null,
      investedAmount: payload.investedAmount ?? null,
    }),
  })
}

export type HoldingCreatePayload = {
  instrumentId?: string
  symbol?: string
  name?: string
  quantity: number
  occurredOn?: string
  unitPrice?: number
  note?: string
  addToInvested?: boolean
}

export function addHolding(kind: PortfolioKind, portfolioId: string, payload: HoldingCreatePayload) {
  return api<Holding>(`${base(kind)}/${portfolioId}/holdings`, {
    method: 'POST',
    body: JSON.stringify({
      instrumentId: payload.instrumentId || null,
      symbol: payload.symbol || null,
      name: payload.name || null,
      quantity: payload.quantity,
      occurredOn: payload.occurredOn || null,
      unitPrice: payload.unitPrice ?? null,
      note: payload.note || null,
      addToInvested: payload.addToInvested ?? true,
    }),
  })
}

export function seedLazyStock(
  portfolioId: string,
  payload: {
    investedAmount: number
    holdings: Array<{
      instrumentId: string
      symbol: string
      name?: string
      quantity: number
      occurredOn: string
      unitPrice: number
    }>
  },
) {
  return api<PortfolioDetail>(`${base('stock')}/${portfolioId}/lazy-seed`, {
    method: 'POST',
    body: JSON.stringify({
      investedAmount: payload.investedAmount,
      holdings: payload.holdings.map((h) => ({
        instrumentId: h.instrumentId,
        symbol: h.symbol,
        name: h.name || null,
        quantity: h.quantity,
        occurredOn: h.occurredOn,
        unitPrice: h.unitPrice,
        note: null,
      })),
    }),
  })
}

export function seedLazyCrypto(
  portfolioId: string,
  payload: {
    investedAmount: number
    startedOn: string
    holdings: Array<{ instrumentId: string; symbol: string; quantity: number }>
  },
) {
  return api<PortfolioDetail>(`${base('crypto')}/${portfolioId}/lazy-seed`, {
    method: 'POST',
    body: JSON.stringify({
      investedAmount: payload.investedAmount,
      startedOn: payload.startedOn,
      holdings: payload.holdings.map((h) => ({
        instrumentId: h.instrumentId,
        symbol: h.symbol,
        quantity: h.quantity,
      })),
    }),
  })
}

export function buyHolding(kind: PortfolioKind, portfolioId: string, holdingId: string, payload: TradePayload) {
  return api<Holding>(`${base(kind)}/${portfolioId}/holdings/${holdingId}/buys`, {
    method: 'POST',
    body: JSON.stringify({
      quantity: payload.quantity,
      occurredOn: payload.occurredOn || null,
      unitPrice: payload.unitPrice ?? null,
      note: payload.note || null,
      addToInvested: payload.addToInvested ?? true,
    }),
  })
}

export function sellHolding(kind: PortfolioKind, portfolioId: string, holdingId: string, payload: TradePayload) {
  return api<Holding>(`${base(kind)}/${portfolioId}/holdings/${holdingId}/sells`, {
    method: 'POST',
    body: JSON.stringify({
      quantity: payload.quantity,
      occurredOn: payload.occurredOn || null,
      unitPrice: payload.unitPrice ?? null,
      note: payload.note || null,
    }),
  })
}

export function listTransactions(kind: PortfolioKind, portfolioId: string, holdingId: string) {
  return api<Trade[]>(`${base(kind)}/${portfolioId}/holdings/${holdingId}/transactions`)
}

export function listTransactionsPaged(
  kind: PortfolioKind,
  portfolioId: string,
  holdingId: string,
  page = 0,
  size = 10,
) {
  const params = new URLSearchParams({ page: String(page), size: String(size) })
  return api<Page<Trade>>(`${base(kind)}/${portfolioId}/holdings/${holdingId}/transactions?${params}`)
}

export type CashMovementPayload = {
  amount: number
  occurredOn?: string
  note?: string
  relatedHoldingId?: string
  /** When false, dividend/coupon counts as income but does not credit cash. Default true. */
  settleToCash?: boolean
}

function cashBody(payload: CashMovementPayload) {
  const body: Record<string, unknown> = {
    amount: payload.amount,
    occurredOn: payload.occurredOn || null,
    note: payload.note || null,
    relatedHoldingId: payload.relatedHoldingId || null,
  }
  if (payload.settleToCash !== undefined) {
    body.settleToCash = payload.settleToCash
  }
  return JSON.stringify(body)
}

export function depositCash(kind: PortfolioKind, portfolioId: string, payload: CashMovementPayload) {
  return api<Holding>(`${base(kind)}/${portfolioId}/cash/deposits`, {
    method: 'POST',
    body: cashBody(payload),
  })
}

export function withdrawCash(kind: PortfolioKind, portfolioId: string, payload: CashMovementPayload) {
  return api<Holding>(`${base(kind)}/${portfolioId}/cash/withdrawals`, {
    method: 'POST',
    body: cashBody(payload),
  })
}

export function addDividend(portfolioId: string, payload: CashMovementPayload) {
  return api<Holding>(`${base('stock')}/${portfolioId}/cash/dividends`, {
    method: 'POST',
    body: cashBody(payload),
  })
}

export function addCoupon(portfolioId: string, payload: CashMovementPayload) {
  return api<Holding>(`${base('stock')}/${portfolioId}/cash/coupons`, {
    method: 'POST',
    body: cashBody(payload),
  })
}

export function getPassiveIncome(portfolioId: string, year?: number) {
  const q = year != null ? `?year=${year}` : ''
  return api<PassiveIncome>(`${base('stock')}/${portfolioId}/passive-income${q}`)
}

export function updatePortfolioSettings(
  portfolioId: string,
  payload: { name: string; taxRatePercent: number; investedAmount: number },
) {
  return api<{ name: string; taxRatePercent: number; investedAmount: number | null }>(
    `${base('stock')}/${portfolioId}/settings`,
    {
      method: 'PATCH',
      body: JSON.stringify(payload),
    },
  )
}

export function getPaymentCalendar(portfolioId: string, year?: number) {
  const q = year != null ? `?year=${year}` : ''
  return api<PaymentCalendar>(`${base('stock')}/${portfolioId}/payment-calendar${q}`)
}

export function getHoldingCashflow(portfolioId: string, holdingId: string) {
  return api<HoldingCashflow>(`${base('stock')}/${portfolioId}/holdings/${holdingId}/cashflow`)
}

export function updateHoldingCashflow(
  portfolioId: string,
  holdingId: string,
  payload: {
    assetKind?: string
    annualCashflowPerUnit?: number | null
    cashflowGrowthPct?: number | null
    cashflowUntilYear?: number | null
  },
) {
  return api<HoldingCashflow>(`${base('stock')}/${portfolioId}/holdings/${holdingId}/cashflow`, {
    method: 'PATCH',
    body: JSON.stringify({
      assetKind: payload.assetKind || null,
      annualCashflowPerUnit: payload.annualCashflowPerUnit ?? null,
      cashflowGrowthPct: payload.cashflowGrowthPct ?? null,
      cashflowUntilYear: payload.cashflowUntilYear ?? null,
    }),
  })
}
