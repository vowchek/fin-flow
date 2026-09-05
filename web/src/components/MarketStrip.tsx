import { useEffect, useState } from 'react'
import { ApiError } from '../api/client'
import * as marketApi from '../api/market'
import type { MarketStripItem, PortfolioKind } from '../api/types'
import { changeClass } from '../lib/format'

type Props = {
  kind: PortfolioKind
}

const SHORT_LABELS: Record<string, string> = {
  imoex: 'IMOEX',
  usd000utstom: 'USD',
  sp500: 'S&P',
  nasdaq: 'NDX',
  gldrub_tom: 'Au',
  brent: 'Brent',
  btc: 'BTC',
  eth: 'ETH',
  'btc-dom': 'Dom',
  'crypto-mcap': 'Cap',
}

function shortLabel(item: MarketStripItem) {
  return SHORT_LABELS[item.id] ?? item.label
}

function formatStripValue(item: MarketStripItem) {
  const n = Number(item.value)
  if (!Number.isFinite(n)) return '—'
  if (item.id === 'crypto-mcap' || Math.abs(n) >= 1_000_000_000_000) {
    return `${item.displayPrefix}${(n / 1_000_000_000_000).toFixed(2)}T${item.displaySuffix}`
  }
  if (Math.abs(n) >= 1_000_000_000) {
    return `${item.displayPrefix}${(n / 1_000_000_000).toFixed(2)}B${item.displaySuffix}`
  }
  if (item.displaySuffix === '%') {
    return `${n.toFixed(1)}%`
  }
  const digits = Math.abs(n) >= 1000 ? 0 : Math.abs(n) < 10 ? 2 : 1
  const formatted = new Intl.NumberFormat('en-US', {
    maximumFractionDigits: digits,
    minimumFractionDigits: 0,
  }).format(n)
  return `${item.displayPrefix}${formatted}${item.displaySuffix}`
}

function formatStripPct(value: number) {
  const sign = value > 0 ? '+' : ''
  return `${sign}${value.toFixed(1)}%`
}

export function MarketStrip({ kind }: Props) {
  const [items, setItems] = useState<MarketStripItem[]>([])
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    setError(null)
    void marketApi
      .getMarketStrip(kind)
      .then((data) => {
        if (!cancelled) setItems(data)
      })
      .catch((err) => {
        if (!cancelled) {
          setItems([])
          setError(err instanceof ApiError ? err.message : 'Не удалось загрузить индикаторы')
        }
      })
    return () => {
      cancelled = true
    }
  }, [kind])

  if (error && items.length === 0) {
    return (
      <div className="market-strip market-strip--empty">
        <span className="muted">{error}</span>
      </div>
    )
  }
  if (items.length === 0) {
    return (
      <div className="market-strip market-strip--empty">
        <span className="muted">Индикаторы рынка…</span>
      </div>
    )
  }

  return (
    <div className="market-strip" aria-label="Рыночные индикаторы">
      {items.map((item) => (
        <div key={item.id} className="market-chip">
          <span className="market-chip-label">{shortLabel(item)}</span>
          <span className="market-chip-value">{formatStripValue(item)}</span>
          {item.changePct != null ? (
            <span className={`market-chip-chg ${changeClass(item.changePct)}`}>
              {formatStripPct(item.changePct)}
            </span>
          ) : null}
        </div>
      ))}
    </div>
  )
}
