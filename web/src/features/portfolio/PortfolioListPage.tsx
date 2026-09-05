import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { ApiError } from '../../api/client'
import * as portfoliosApi from '../../api/portfolios'
import type { Holding, PortfolioKind, PortfolioSummary } from '../../api/types'
import { MarketStrip } from '../../components/MarketStrip'
import { AssetTickerStrip } from '../../components/AssetTickerStrip'
import { PortfolioChart } from '../../components/PortfolioChart'
import { PortfolioKindIcon } from '../../components/PortfolioKindIcon'
import { changeClass, formatMoney, formatPct } from '../../lib/format'
import { CreatePortfolioWizard } from './CreatePortfolioWizard'

type Props = {
  kind: PortfolioKind
  title: string
  basePath: string
}

function topHoldings(holdings: Holding[]) {
  return [...holdings]
    .filter((h) => Number(h.quantity) > 0)
    .sort((a, b) => {
      if (Boolean(a.cash) !== Boolean(b.cash)) return a.cash ? 1 : -1
      return Number(b.marketValue ?? b.quantity) - Number(a.marketValue ?? a.quantity)
    })
}

function dayMovers(holdings: Holding[]) {
  const ranked = holdings
    .filter((h) => !h.cash && Number(h.quantity) > 0 && h.dayChangePct != null && Number.isFinite(Number(h.dayChangePct)))
    .sort((a, b) => Number(b.dayChangePct) - Number(a.dayChangePct))
  if (ranked.length === 0) return { best: null, worst: null }
  const best = ranked[0]
  const worst = ranked[ranked.length - 1]
  if (best.id === worst.id) return { best, worst: null }
  return { best, worst }
}

export function PortfolioListPage({ kind, title, basePath }: Props) {
  const [items, setItems] = useState<PortfolioSummary[]>([])
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [createOpen, setCreateOpen] = useState(false)

  async function reload() {
    setLoading(true)
    setError(null)
    try {
      setItems(await portfoliosApi.listPortfolios(kind))
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Ошибка загрузки')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void reload()
  }, [kind])

  const currency = kind === 'crypto' ? 'USD' : 'RUB'

  return (
    <div className="page">
      <div className="page-head">
        <div>
          <h1 className="page-title page-title-with-icon">
            <span className="page-kind-icon" aria-hidden>
              <PortfolioKindIcon kind={kind} size={28} />
            </span>
            {title}
          </h1>
        </div>
        <button type="button" className="btn" onClick={() => setCreateOpen(true)}>
          Новый портфель
        </button>
      </div>

      <MarketStrip kind={kind} />

      {error && !createOpen ? <p className="error">{error}</p> : null}

      {loading ? (
        <div className="panel empty">Загрузка…</div>
      ) : items.length === 0 ? (
        <div className="panel empty">Пока нет портфелей — создайте первый.</div>
      ) : (
        <div className="portfolio-grid">
          {items.map((item) => {
            const itemCurrency = item.currency || currency
            const ranked = topHoldings(item.holdings ?? [])
            const tickerSymbols = ranked.filter((h) => !h.cash).map((h) => h.symbol)
            const { best, worst } = dayMovers(item.holdings ?? [])
            return (
              <article key={item.id} className="pf-card">
                <Link to={`${basePath}/${item.id}`} className="pf-card-link" aria-label={item.name} />
                <div className="pf-card-body">
                  <div className="pf-card-main">
                    <h2 className="pf-card-title">{item.name}</h2>
                    {item.description ? <p className="pf-card-desc">{item.description}</p> : null}

                    <div className="pf-card-metrics">
                      <p className="pf-card-value">{formatMoney(item.totalValue, itemCurrency)}</p>
                      <p className={`pf-card-chg ${changeClass(item.totalChangeAbs)}`}>
                        <span className="pf-card-chg-label">за период</span>
                        <span className="pf-card-chg-abs">{formatMoney(item.totalChangeAbs, itemCurrency)}</span>
                        <span className="pf-card-chg-pct">{formatPct(item.totalChangePct)}</span>
                      </p>
                      <p className={`pf-card-chg ${changeClass(item.dayChangeAbs)}`}>
                        <span className="pf-card-chg-label">за день</span>
                        <span className="pf-card-chg-abs">{formatMoney(item.dayChangeAbs, itemCurrency)}</span>
                        <span className="pf-card-chg-pct">{formatPct(item.dayChangePct)}</span>
                      </p>
                    </div>

                    {best || worst ? (
                      <div className="pf-card-movers">
                        {best ? (
                          <div className={`pf-mover ${changeClass(best.dayChangePct)}`}>
                            <span className="pf-mover-label">лучш.</span>
                            <span className="pf-mover-sym">{best.symbol}</span>
                            <span className="pf-mover-pct">{formatPct(best.dayChangePct)}</span>
                          </div>
                        ) : null}
                        {worst ? (
                          <div className={`pf-mover ${changeClass(worst.dayChangePct)}`}>
                            <span className="pf-mover-label">худш.</span>
                            <span className="pf-mover-sym">{worst.symbol}</span>
                            <span className="pf-mover-pct">{formatPct(worst.dayChangePct)}</span>
                          </div>
                        ) : null}
                      </div>
                    ) : null}
                  </div>
                  <div className="pf-card-chart">
                    <PortfolioChart holdings={ranked} compact />
                  </div>
                </div>

                <AssetTickerStrip symbols={tickerSymbols} />
              </article>
            )
          })}
        </div>
      )}

      <CreatePortfolioWizard
        open={createOpen}
        kind={kind}
        basePath={basePath}
        onClose={() => setCreateOpen(false)}
        onCreated={() => void reload()}
      />
    </div>
  )
}
