import { useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { ApiError } from '../../api/client'
import * as portfoliosApi from '../../api/portfolios'
import type { Holding, PortfolioKind, PortfolioSummary } from '../../api/types'
import { Modal } from '../../components/Modal'
import { PortfolioChart } from '../../components/PortfolioChart'
import { changeClass, formatMoney, formatPct } from '../../lib/format'

type Props = {
  kind: PortfolioKind
  title: string
  basePath: string
}

const MAX_TICKERS = 5

function topHoldings(holdings: Holding[]) {
  return [...holdings]
    .filter((h) => Number(h.quantity) > 0)
    .sort((a, b) => Number(b.marketValue ?? b.quantity) - Number(a.marketValue ?? a.quantity))
}

export function PortfolioListPage({ kind, title, basePath }: Props) {
  const [items, setItems] = useState<PortfolioSummary[]>([])
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [createOpen, setCreateOpen] = useState(false)
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [saving, setSaving] = useState(false)

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

  async function onCreate(e: FormEvent) {
    e.preventDefault()
    setSaving(true)
    setError(null)
    try {
      await portfoliosApi.createPortfolio(kind, name, description || undefined)
      setName('')
      setDescription('')
      setCreateOpen(false)
      await reload()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Не удалось создать')
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="page">
      <div className="page-head">
        <div>
          <h1 className="page-title">{title}</h1>
          <p className="page-lead">Стоимость, прибыль и состав. Нажмите на название, чтобы открыть.</p>
        </div>
        <button type="button" className="btn" onClick={() => setCreateOpen(true)}>
          Новый портфель
        </button>
      </div>

      {error && !createOpen && <p className="error">{error}</p>}

      {loading ? (
        <div className="panel empty">Загрузка…</div>
      ) : items.length === 0 ? (
        <div className="panel empty">Пока нет портфелей — создайте первый</div>
      ) : (
        <div className="portfolio-grid">
          {items.map((item) => {
            const currency = item.currency || (kind === 'crypto' ? 'USD' : 'RUB')
            const ranked = topHoldings(item.holdings ?? [])
            const shown = ranked.slice(0, MAX_TICKERS)
            const more = ranked.length - shown.length
            return (
              <article key={item.id} className="pf-card">
                <div className="pf-card-main">
                  <Link to={`${basePath}/${item.id}`} className="pf-card-title">
                    {item.name}
                  </Link>
                  {item.description ? <p className="pf-card-desc">{item.description}</p> : null}

                  <p className="pf-card-value">{formatMoney(item.totalValue, currency)}</p>
                  <p className={`pf-card-pnl ${changeClass(item.totalChangeAbs)}`}>
                    {formatMoney(item.totalChangeAbs, currency)}
                    <span>{formatPct(item.totalChangePct)}</span>
                  </p>
                  <p className={`pf-card-day ${changeClass(item.dayChangeAbs)}`}>
                    за день {formatMoney(item.dayChangeAbs, currency)} {formatPct(item.dayChangePct)}
                  </p>

                  <div className="pf-card-tickers">
                    {shown.length === 0 ? (
                      <span className="muted">Нет позиций</span>
                    ) : (
                      <>
                        {shown.map((h) => (
                          <span key={h.id} className="pf-ticker">
                            {h.symbol}
                          </span>
                        ))}
                        {more > 0 ? <span className="pf-ticker more">+{more}</span> : null}
                      </>
                    )}
                  </div>
                </div>
                <div className="pf-card-chart">
                  <PortfolioChart holdings={ranked} compact />
                </div>
              </article>
            )
          })}
        </div>
      )}

      <Modal open={createOpen} title="Новый портфель" onClose={() => !saving && setCreateOpen(false)}>
        <form className="stack" onSubmit={onCreate}>
          <div className="field">
            <label htmlFor="pf-name">Название</label>
            <input id="pf-name" required autoFocus value={name} onChange={(e) => setName(e.target.value)} />
          </div>
          <div className="field">
            <label htmlFor="pf-desc">Описание</label>
            <input id="pf-desc" value={description} onChange={(e) => setDescription(e.target.value)} />
          </div>
          {error && createOpen && <p className="error">{error}</p>}
          <div className="modal-actions">
            <button type="button" className="btn btn-ghost" disabled={saving} onClick={() => setCreateOpen(false)}>
              Отмена
            </button>
            <button type="submit" className="btn" disabled={saving}>
              {saving ? 'Создаём…' : 'Создать'}
            </button>
          </div>
        </form>
      </Modal>
    </div>
  )
}
