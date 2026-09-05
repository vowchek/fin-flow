import { api } from './client'
import type { Instrument, PortfolioKind } from './types'

function userPath(kind: PortfolioKind) {
  return kind === 'stock' ? '/api/v1/stock-instruments' : '/api/v1/crypto-instruments'
}

function adminPath(kind: PortfolioKind) {
  return kind === 'stock' ? '/api/v1/admin/stock-instruments' : '/api/v1/admin/crypto-instruments'
}

export type InstrumentPayload = {
  symbol: string
  externalId: string
  name: string
  currency?: string
  enabled?: boolean
  assetKind?: string | null
  annualCashflowPerUnit?: number | null
  cashflowGrowthPct?: number | null
  cashflowUntilYear?: number | null
}

function body(kind: PortfolioKind, payload: InstrumentPayload) {
  const base = {
    symbol: payload.symbol,
    externalId: payload.externalId,
    name: payload.name,
    currency: payload.currency || (kind === 'crypto' ? 'USD' : 'RUB'),
    enabled: payload.enabled ?? true,
  }
  if (kind !== 'stock') return base
  return {
    ...base,
    assetKind: payload.assetKind || 'EQUITY',
    annualCashflowPerUnit: payload.annualCashflowPerUnit ?? null,
    cashflowGrowthPct: payload.cashflowGrowthPct ?? null,
    cashflowUntilYear: payload.cashflowUntilYear ?? null,
  }
}

export function listCatalog(kind: PortfolioKind, q?: string) {
  const params = q?.trim() ? `?q=${encodeURIComponent(q.trim())}` : ''
  return api<Instrument[]>(`${userPath(kind)}${params}`)
}

export function listAdminCatalog(kind: PortfolioKind) {
  return api<Instrument[]>(adminPath(kind))
}

export function createInstrument(kind: PortfolioKind, payload: InstrumentPayload) {
  return api<Instrument>(adminPath(kind), {
    method: 'POST',
    body: JSON.stringify(body(kind, payload)),
  })
}

export function updateInstrument(kind: PortfolioKind, id: string, payload: InstrumentPayload) {
  return api<Instrument>(`${adminPath(kind)}/${id}`, {
    method: 'PUT',
    body: JSON.stringify(body(kind, payload)),
  })
}

export function deleteInstrument(kind: PortfolioKind, id: string) {
  return api<void>(`${adminPath(kind)}/${id}`, { method: 'DELETE' })
}

export async function uploadLogo(kind: PortfolioKind, id: string, file: File) {
  const token = localStorage.getItem('ledger_token')
  const bodyForm = new FormData()
  bodyForm.append('file', file)
  const response = await fetch(`${import.meta.env.VITE_API_URL ?? ''}${adminPath(kind)}/${id}/logo`, {
    method: 'POST',
    headers: token ? { Authorization: `Bearer ${token}` } : undefined,
    body: bodyForm,
  })
  const text = await response.text()
  const data = text ? JSON.parse(text) : null
  if (!response.ok) {
    throw new Error(data?.detail ?? response.statusText)
  }
  return data as Instrument
}
