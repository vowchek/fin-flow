import { api } from './client'
import type { MarketStripItem, PortfolioKind } from './types'

export function getMarketStrip(kind: PortfolioKind) {
  const path = kind === 'crypto' ? '/api/v1/market/crypto-strip' : '/api/v1/market/stock-strip'
  return api<MarketStripItem[]>(path)
}
