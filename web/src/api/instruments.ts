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
  paysDividends?: boolean | null
}

export type RemoteInstrument = {
  symbol: string
  externalId: string
  name: string
  currency: string
  logoUrl: string | null
}

export type InstrumentPayment = {
  id: string
  occurredOn: string
  amountPerUnit: number
  currency: string
  kind: 'DIVIDEND' | 'COUPON'
}

export type InstrumentImportResult = {
  instrument: Instrument
  payments: InstrumentPayment[]
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
    paysDividends: payload.paysDividends ?? true,
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

export function searchMoex(q: string) {
  return api<RemoteInstrument[]>(`/api/v1/admin/market/stock/search?q=${encodeURIComponent(q.trim())}`)
}

export function importFromMoex(payload: {
  externalId?: string
  symbol?: string
  enabled?: boolean
  overwriteCashflow?: boolean
}) {
  return api<InstrumentImportResult>('/api/v1/admin/stock-instruments/import', {
    method: 'POST',
    body: JSON.stringify({
      externalId: payload.externalId ?? null,
      symbol: payload.symbol ?? null,
      enabled: payload.enabled ?? true,
      overwriteCashflow: payload.overwriteCashflow ?? true,
    }),
  })
}

export function refreshFromMoex(id: string, overwriteCashflow = true) {
  const q = overwriteCashflow ? '?overwriteCashflow=true' : '?overwriteCashflow=false'
  return api<InstrumentImportResult>(`${adminPath('stock')}/${id}/refresh${q}`, { method: 'POST' })
}

export type PaymentsRefreshResult = {
  refreshed: number
  failed: number
  results: Array<{
    id: string
    symbol: string
    ok: boolean
    error: string | null
    paymentCount: number
  }>
}

export function refreshAllPayments() {
  return api<PaymentsRefreshResult>(`${adminPath('stock')}/refresh-payments`, { method: 'POST' })
}

export function listInstrumentPayments(id: string) {
  return api<InstrumentPayment[]>(`${adminPath('stock')}/${id}/payments`)
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
