import { api } from './client'

import type { Holding, PortfolioDetail, PortfolioKind, PortfolioSummary, Trade, TradePayload } from './types'



function base(kind: PortfolioKind) {

  return kind === 'stock' ? '/api/v1/stock-portfolios' : '/api/v1/crypto-portfolios'

}



export function listPortfolios(kind: PortfolioKind) {

  return api<PortfolioSummary[]>(base(kind))

}



export function getPortfolio(kind: PortfolioKind, id: string) {

  return api<PortfolioDetail>(`${base(kind)}/${id}`)

}



export function createPortfolio(kind: PortfolioKind, name: string, description?: string) {

  return api<PortfolioDetail>(base(kind), {

    method: 'POST',

    body: JSON.stringify({ name, description: description || null }),

  })

}



export function deletePortfolio(kind: PortfolioKind, id: string) {

  return api<void>(`${base(kind)}/${id}`, { method: 'DELETE' })

}



export function addHolding(

  kind: PortfolioKind,

  portfolioId: string,

  payload: { instrumentId?: string; symbol?: string; name?: string; quantity: number; occurredOn?: string },

) {

  return api<Holding>(`${base(kind)}/${portfolioId}/holdings`, {

    method: 'POST',

    body: JSON.stringify({

      instrumentId: payload.instrumentId || null,

      symbol: payload.symbol || null,

      name: payload.name || null,

      quantity: payload.quantity,

      occurredOn: payload.occurredOn || null,

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


