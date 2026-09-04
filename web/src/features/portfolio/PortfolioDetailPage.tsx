import { useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ApiError } from '../../api/client'
import * as instrumentsApi from '../../api/instruments'
import * as portfoliosApi from '../../api/portfolios'
import type { Holding, Instrument, PortfolioDetail, PortfolioKind, Trade } from '../../api/types'
import { AssetLogo } from '../../components/AssetLogo'
import {
  BuyIcon,
  EyeIcon,
  EyeOffIcon,
  HistoryIcon,
  IconButton,
  PlusIcon,
  ProfitIcon,
  SellIcon,
  ValueIcon,
} from '../../components/IconButton'
import { Modal } from '../../components/Modal'
import { changeClass, formatMoney, formatPct } from '../../lib/format'

type Props = {
  kind: PortfolioKind
  listPath: string
  title: string
}

type Dialog =
  | { type: 'add' }
  | { type: 'buy'; holding: Holding }
  | { type: 'sell'; holding: Holding }
  | { type: 'history'; holding: Holding }
  | { type: 'deletePortfolio' }
  | null

function todayIso() {
  return new Date().toISOString().slice(0, 10)
}

function avgPrice(h: Holding) {
  if (h.costBasis == null || Number(h.quantity) <= 0) return null
  return Number(h.costBasis) / Number(h.quantity)
}

function sharePct(h: Holding, total: number | null | undefined) {
  if (h.marketValue == null || total == null || Number(total) <= 0) return null
  return (Number(h.marketValue) / Number(total)) * 100
}

export function PortfolioDetailPage({ kind, listPath, title }: Props) {
  const { id = '' } = useParams()
  const navigate = useNavigate()
  const [portfolio, setPortfolio] = useState<PortfolioDetail | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [dialog, setDialog] = useState<Dialog>(null)
  const [pending, setPending] = useState(false)

  const [query, setQuery] = useState('')
  const [catalog, setCatalog] = useState<Instrument[]>([])
  const [catalogLoading, setCatalogLoading] = useState(false)
  const [selected, setSelected] = useState<Instrument | null>(null)
  const [quantity, setQuantity] = useState('1')
  const [occurredOn, setOccurredOn] = useState(todayIso())
  const [trades, setTrades] = useState<Trade[]>([])
  const [historyLoading, setHistoryLoading] = useState(false)
  const [showClosed, setShowClosed] = useState(false)

  const filteredCatalog = (() => {
    const q = query.trim().toLowerCase()
    if (!q) return []
    return catalog.filter(
      (item) =>
        item.symbol.toLowerCase().includes(q) ||
        item.name.toLowerCase().includes(q) ||
        item.externalId.toLowerCase().includes(q),
    )
  })()

  async function reload() {
    setLoading(true)
    setError(null)
    try {
      setPortfolio(await portfoliosApi.getPortfolio(kind, id))
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Ошибка загрузки')
      setPortfolio(null)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void reload()
  }, [kind, id])

  function resetTradeForm() {
    setQuantity('1')
    setOccurredOn(todayIso())
    setError(null)
  }

  function openAdd() {
    setQuery('')
    setSelected(null)
    resetTradeForm()
    setDialog({ type: 'add' })
    setCatalogLoading(true)
    void instrumentsApi
      .listCatalog(kind)
      .then(setCatalog)
      .catch(() => setCatalog([]))
      .finally(() => setCatalogLoading(false))
  }

  async function openHistory(holding: Holding) {
    setError(null)
    setTrades([])
    setDialog({ type: 'history', holding })
    setHistoryLoading(true)
    try {
      setTrades(await portfoliosApi.listTransactions(kind, id, holding.id))
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Не удалось загрузить историю')
    } finally {
      setHistoryLoading(false)
    }
  }

  async function onAdd(e: FormEvent) {
    e.preventDefault()
    if (!selected) {
      setError('Выберите инструмент из списка')
      return
    }
    setPending(true)
    setError(null)
    try {
      await portfoliosApi.addHolding(kind, id, {
        instrumentId: selected.id,
        symbol: selected.symbol,
        name: selected.name,
        quantity: Number(quantity),
        occurredOn,
      })
      setDialog(null)
      await reload()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Не удалось добавить')
    } finally {
      setPending(false)
    }
  }

  async function onBuy(e: FormEvent) {
    e.preventDefault()
    if (dialog?.type !== 'buy') return
    setPending(true)
    setError(null)
    try {
      await portfoliosApi.buyHolding(kind, id, dialog.holding.id, { quantity: Number(quantity), occurredOn })
      setDialog(null)
      await reload()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Не удалось купить')
    } finally {
      setPending(false)
    }
  }

  async function onSell(e: FormEvent) {
    e.preventDefault()
    if (dialog?.type !== 'sell') return
    setPending(true)
    setError(null)
    try {
      await portfoliosApi.sellHolding(kind, id, dialog.holding.id, { quantity: Number(quantity), occurredOn })
      setDialog(null)
      await reload()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Не удалось продать')
    } finally {
      setPending(false)
    }
  }

  async function onDeletePortfolio() {
    setPending(true)
    setError(null)
    try {
      await portfoliosApi.deletePortfolio(kind, id)
      navigate(listPath)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Не удалось удалить портфель')
      setPending(false)
    }
  }

  if (loading) {
    return (
      <div className="page">
        <div className="panel empty">Загрузка…</div>
      </div>
    )
  }

  if (!portfolio) {
    return (
      <div className="page">
        <p className="error">{error || 'Портфель не найден'}</p>
        <Link to={listPath}>Назад</Link>
      </div>
    )
  }

  const currency = portfolio.currency || (kind === 'crypto' ? 'USD' : 'RUB')
  const openHoldings = [...portfolio.holdings]
    .filter((h) => Number(h.quantity) > 0)
    .sort((a, b) => Number(b.marketValue ?? 0) - Number(a.marketValue ?? 0))
  const closedHoldings = portfolio.holdings.filter((h) => Number(h.quantity) <= 0)
  const rows = showClosed ? [...openHoldings, ...closedHoldings] : openHoldings
  const invested = openHoldings.reduce<number | null>((sum, h) => {
    if (h.costBasis == null) return sum
    return (sum ?? 0) + Number(h.costBasis)
  }, null)

  return (
    <div className="page page-wide">
      <p className="crumb">
        <Link to={listPath}>{title}</Link> / {portfolio.name}
      </p>

      <div className="page-head">
        <div>
          <h1 className="page-title">{portfolio.name}</h1>
          {portfolio.description ? <p className="page-lead">{portfolio.description}</p> : null}
        </div>
        <button type="button" className="btn btn-danger" onClick={() => setDialog({ type: 'deletePortfolio' })}>
          Удалить
        </button>
      </div>

      {error && !dialog && <p className="error">{error}</p>}

      <div className="pf-metrics">
        <section className="pf-metric pf-metric--value">
          <div className="pf-metric-top">
            <span className="pf-metric-icon" aria-hidden>
              <ValueIcon />
            </span>
            <h2 className="pf-metric-title">Стоимость</h2>
          </div>
          <p className="pf-metric-hero">{formatMoney(portfolio.totalValue, currency)}</p>
          <p className="pf-metric-sub">
            <span>Вложено</span>
            <strong>{formatMoney(invested, currency)}</strong>
          </p>
        </section>

        <section className={`pf-metric pf-metric--pnl ${changeClass(portfolio.totalChangeAbs)}`}>
          <div className="pf-metric-top">
            <span className="pf-metric-icon" aria-hidden>
              <ProfitIcon />
            </span>
            <h2 className="pf-metric-title">Прибыль</h2>
          </div>
          <p className={`pf-metric-hero ${changeClass(portfolio.totalChangeAbs)}`}>
            {formatMoney(portfolio.totalChangeAbs, currency)}
            <span className="pf-metric-pct">{formatPct(portfolio.totalChangePct)}</span>
          </p>
          <p className="pf-metric-sub">
            <span>За сегодня</span>
            <strong className={changeClass(portfolio.dayChangeAbs)}>
              {formatMoney(portfolio.dayChangeAbs, currency)} {formatPct(portfolio.dayChangePct)}
            </strong>
          </p>
        </section>
      </div>

      <section className="panel table-panel">
        <div className="section-title-row">
          <h2>Позиции</h2>
          <div className="row" style={{ gap: '0.5rem' }}>
            {closedHoldings.length > 0 ? (
              <button
                type="button"
                className="btn btn-ghost btn-sm"
                onClick={() => setShowClosed((v) => !v)}
              >
                <span className="row" style={{ gap: '0.35rem' }}>
                  {showClosed ? <EyeOffIcon /> : <EyeIcon />}
                  {showClosed ? 'Скрыть проданные' : `Проданные (${closedHoldings.length})`}
                </span>
              </button>
            ) : null}
            <button type="button" className="icon-btn" aria-label="Добавить актив" onClick={openAdd}>
              <PlusIcon />
            </button>
          </div>
        </div>

        {rows.length === 0 ? (
          <div className="empty">
            {openHoldings.length === 0 && closedHoldings.length > 0
              ? 'Открытых позиций нет — покажите проданные'
              : 'Активов пока нет'}
          </div>
        ) : (
          <div className="asset-table-wrap">
            <table className="asset-table">
              <thead>
                <tr>
                  <th>Актив</th>
                  <th>Кол-во</th>
                  <th>Вложено</th>
                  <th>Сейчас</th>
                  <th>Прибыль</th>
                  <th>Доля</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {rows.map((h) => {
                  const closed = Number(h.quantity) <= 0
                  const cur = h.currency || currency
                  const avg = avgPrice(h)
                  const share = sharePct(h, portfolio.totalValue)
                  return (
                    <tr key={h.id} className={closed ? 'closed' : undefined}>
                      <td>
                        <div className="asset-cell">
                          <AssetLogo symbol={h.symbol} name={h.name} logoUrl={h.logoUrl} size={32} />
                          <div>
                            <div className="asset-name">
                              {h.name || h.symbol}
                              {closed ? <span className="holding-badge">закрыта</span> : null}
                            </div>
                            <div className="asset-ticker">{h.symbol}</div>
                          </div>
                        </div>
                      </td>
                      <td>
                        <div className="cell-main">{h.quantity}</div>
                        <div className="cell-sub">шт</div>
                      </td>
                      <td>
                        <div className="cell-main">{formatMoney(h.costBasis, cur)}</div>
                        <div className="cell-sub">{formatMoney(avg, cur)} / шт</div>
                      </td>
                      <td>
                        <div className="cell-main">{formatMoney(h.marketValue, cur)}</div>
                        <div className="cell-sub">{formatMoney(h.unitPrice, cur)} / шт</div>
                      </td>
                      <td>
                        <div className={`cell-main ${changeClass(h.totalChangeAbs)}`}>
                          {formatMoney(h.totalChangeAbs, cur)}
                        </div>
                        <div className={`cell-sub ${changeClass(h.totalChangePct)}`}>{formatPct(h.totalChangePct)}</div>
                      </td>
                      <td>
                        <div className="cell-main">{share == null ? '—' : `${share.toFixed(1)}%`}</div>
                        <div className="cell-sub">портфеля</div>
                      </td>
                      <td>
                        <span className="holding-actions">
                          <IconButton
                            label="Купить"
                            onClick={() => {
                              resetTradeForm()
                              setDialog({ type: 'buy', holding: h })
                            }}
                          >
                            <BuyIcon />
                          </IconButton>
                          <IconButton
                            label="Продать"
                            onClick={() => {
                              resetTradeForm()
                              setDialog({ type: 'sell', holding: h })
                            }}
                          >
                            <SellIcon />
                          </IconButton>
                          <IconButton label="История" onClick={() => void openHistory(h)}>
                            <HistoryIcon />
                          </IconButton>
                        </span>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
      </section>

      <Modal open={dialog?.type === 'add'} title="Добавить актив" onClose={() => !pending && setDialog(null)}>
        <form className="stack" onSubmit={onAdd}>
          <div className="field">
            <label htmlFor="instrument-search">Поиск в каталоге</label>
            {selected ? (
              <div className="selected-instrument">
                <AssetLogo symbol={selected.symbol} name={selected.name} logoUrl={selected.logoUrl} />
                <span>
                  <strong>{selected.symbol}</strong>
                  <span className="holding-meta">{selected.name}</span>
                </span>
                <button type="button" className="btn btn-ghost" onClick={() => { setSelected(null); setQuery('') }}>
                  Сменить
                </button>
              </div>
            ) : (
              <>
                <input
                  id="instrument-search"
                  autoFocus
                  placeholder="Начните вводить тикер или название…"
                  value={query}
                  onChange={(e) => setQuery(e.target.value)}
                />
                {catalogLoading ? (
                  <p className="muted">Загрузка каталога…</p>
                ) : !query.trim() ? null : filteredCatalog.length === 0 ? (
                  <p className="muted">{catalog.length === 0 ? 'Каталог пуст' : 'Ничего не найдено'}</p>
                ) : (
                  <ul className="suggest-list">
                    {filteredCatalog.map((item) => (
                      <li key={item.id}>
                        <button
                          type="button"
                          className="suggest-item"
                          onClick={() => {
                            setSelected(item)
                            setQuery('')
                          }}
                        >
                          <AssetLogo symbol={item.symbol} name={item.name} logoUrl={item.logoUrl} size={24} />
                          <span>
                            <strong>{item.symbol}</strong>
                            <span className="holding-meta">{item.name}</span>
                          </span>
                        </button>
                      </li>
                    ))}
                  </ul>
                )}
              </>
            )}
          </div>
          <div className="field">
            <label htmlFor="qty">Количество</label>
            <input id="qty" type="number" step="any" min="0" required value={quantity} onChange={(e) => setQuantity(e.target.value)} />
          </div>
          <div className="field">
            <label htmlFor="occurredOn">Дата покупки</label>
            <input id="occurredOn" type="date" required value={occurredOn} onChange={(e) => setOccurredOn(e.target.value)} />
          </div>
          {error && dialog?.type === 'add' && <p className="error">{error}</p>}
          <div className="modal-actions">
            <button type="button" className="btn btn-ghost" disabled={pending} onClick={() => setDialog(null)}>Отмена</button>
            <button type="submit" className="btn" disabled={pending || !selected}>{pending ? 'Сохраняем…' : 'Добавить'}</button>
          </div>
        </form>
      </Modal>

      <Modal open={dialog?.type === 'buy'} title={dialog?.type === 'buy' ? `Купить ${dialog.holding.symbol}` : 'Купить'} onClose={() => !pending && setDialog(null)}>
        <form className="stack" onSubmit={onBuy}>
          <div className="field">
            <label htmlFor="buy-qty">Количество</label>
            <input id="buy-qty" type="number" step="any" min="0" required autoFocus value={quantity} onChange={(e) => setQuantity(e.target.value)} />
          </div>
          <div className="field">
            <label htmlFor="buy-date">Дата</label>
            <input id="buy-date" type="date" required value={occurredOn} onChange={(e) => setOccurredOn(e.target.value)} />
          </div>
          {error && dialog?.type === 'buy' && <p className="error">{error}</p>}
          <div className="modal-actions">
            <button type="button" className="btn btn-ghost" disabled={pending} onClick={() => setDialog(null)}>Отмена</button>
            <button type="submit" className="btn" disabled={pending}>{pending ? 'Сохраняем…' : 'Купить'}</button>
          </div>
        </form>
      </Modal>

      <Modal open={dialog?.type === 'sell'} title={dialog?.type === 'sell' ? `Продать ${dialog.holding.symbol}` : 'Продать'} onClose={() => !pending && setDialog(null)}>
        <form className="stack" onSubmit={onSell}>
          {dialog?.type === 'sell' && <p className="muted" style={{ marginTop: 0 }}>Доступно: {dialog.holding.quantity}</p>}
          <div className="field">
            <label htmlFor="sell-qty">Количество</label>
            <input id="sell-qty" type="number" step="any" min="0" required autoFocus value={quantity} onChange={(e) => setQuantity(e.target.value)} />
          </div>
          <div className="field">
            <label htmlFor="sell-date">Дата</label>
            <input id="sell-date" type="date" required value={occurredOn} onChange={(e) => setOccurredOn(e.target.value)} />
          </div>
          {error && dialog?.type === 'sell' && <p className="error">{error}</p>}
          <div className="modal-actions">
            <button type="button" className="btn btn-ghost" disabled={pending} onClick={() => setDialog(null)}>Отмена</button>
            <button type="submit" className="btn" disabled={pending}>{pending ? 'Сохраняем…' : 'Продать'}</button>
          </div>
        </form>
      </Modal>

      <Modal open={dialog?.type === 'history'} title={dialog?.type === 'history' ? `История ${dialog.holding.symbol}` : 'История'} onClose={() => setDialog(null)}>
        {historyLoading ? (
          <p className="muted">Загрузка…</p>
        ) : trades.length === 0 ? (
          <p className="muted">Сделок пока нет</p>
        ) : (
          <ul className="trade-list">
            {trades.map((t) => (
              <li key={t.id} className="trade-row">
                <span className={`trade-side ${t.side.toLowerCase()}`}>{t.side === 'BUY' ? 'Покупка' : 'Продажа'}</span>
                <span className="trade-qty">{t.quantity}</span>
                <span className="trade-date">
                  {t.occurredOn}
                  {t.unitPrice != null ? ` · ${formatMoney(t.unitPrice, currency)}` : ''}
                </span>
              </li>
            ))}
          </ul>
        )}
        <div className="modal-actions">
          <button type="button" className="btn btn-ghost" onClick={() => setDialog(null)}>Закрыть</button>
        </div>
      </Modal>

      <Modal open={dialog?.type === 'deletePortfolio'} title="Удалить портфель?" onClose={() => !pending && setDialog(null)}>
        <p className="muted" style={{ marginTop: 0 }}>Будут удалены портфель, позиции и история сделок.</p>
        {error && dialog?.type === 'deletePortfolio' && <p className="error">{error}</p>}
        <div className="modal-actions">
          <button type="button" className="btn btn-ghost" disabled={pending} onClick={() => setDialog(null)}>Отмена</button>
          <button type="button" className="btn btn-danger" disabled={pending} onClick={() => void onDeletePortfolio()}>
            {pending ? 'Удаляем…' : 'Удалить'}
          </button>
        </div>
      </Modal>
    </div>
  )
}
