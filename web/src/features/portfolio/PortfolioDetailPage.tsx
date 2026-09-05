import { useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ApiError } from '../../api/client'
import * as instrumentsApi from '../../api/instruments'
import * as portfoliosApi from '../../api/portfolios'
import type { Holding, Instrument, PassiveIncome, PortfolioDetail, PortfolioKind, Trade } from '../../api/types'
import { AssetLogo } from '../../components/AssetLogo'
import {
  EyeIcon,
  EyeOffIcon,
  GearIcon,
  HistoryIcon,
  IconButton,
  MinusIcon,
  PlusIcon,
} from '../../components/IconButton'
import { Modal } from '../../components/Modal'
import { GrowthChartModal } from '../../components/GrowthChartModal'
import { PaymentCalendarModal } from '../../components/PaymentCalendarModal'
import { PortfolioModeIcon, portfolioModeLabel } from '../../components/PortfolioModeIcon'
import { changeClass, formatMoney, formatPct } from '../../lib/format'
import { LazySeedModal } from './LazySeedModal'

type Props = {
  kind: PortfolioKind
  listPath: string
  title: string
}

type Dialog =
  | { type: 'add' }
  | { type: 'add-choice' }
  | { type: 'buy'; holding: Holding }
  | { type: 'sell'; holding: Holding }
  | { type: 'history'; holding: Holding }
  | { type: 'deletePortfolio' }
  | { type: 'cash-in' }
  | { type: 'cash-deposit' }
  | { type: 'cash-withdraw' }
  | { type: 'cash-dividend' }
  | { type: 'cash-coupon' }
  | { type: 'settings' }
  | null

function todayIso() {
  return new Date().toISOString().slice(0, 10)
}

function avgPrice(h: Holding) {
  if (h.cash) return 1
  if (h.costBasis == null || Number(h.quantity) <= 0) return null
  return Number(h.costBasis) / Number(h.quantity)
}

function sharePct(h: Holding, total: number | null | undefined) {
  if (h.marketValue == null || total == null || Number(total) <= 0) return null
  return (Number(h.marketValue) / Number(total)) * 100
}

function pricePnl(h: Holding) {
  return h.totalChangeAbs
}

function totalReturn(h: Holding) {
  const price = Number(h.totalChangeAbs ?? 0)
  const income = Number(h.incomeAbs ?? 0)
  if (h.totalChangeAbs == null && h.incomeAbs == null) return null
  return price + income
}

function txKindLabel(t: Trade) {
  const outside = t.settleToCash === false
  switch (t.kind) {
    case 'DEPOSIT':
      return 'Пополнение'
    case 'WITHDRAW':
      return 'Вывод'
    case 'DIVIDEND':
      return outside ? 'Дивиденд (реинвест)' : 'Дивиденд'
    case 'COUPON':
      return outside ? 'Купон (реинвест)' : 'Купон'
    default:
      return t.side === 'BUY' ? 'Покупка' : 'Продажа'
  }
}

function txKindClass(t: Trade) {
  if (t.kind === 'DEPOSIT' || t.kind === 'DIVIDEND' || t.kind === 'COUPON') return 'buy'
  if (t.kind === 'WITHDRAW') return 'sell'
  return t.side.toLowerCase()
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
  const [unitPrice, setUnitPrice] = useState('')
  const [occurredOn, setOccurredOn] = useState(todayIso())
  const [trades, setTrades] = useState<Trade[]>([])
  const [historyLoading, setHistoryLoading] = useState(false)
  const [showClosed, setShowClosed] = useState(false)
  const [growthOpen, setGrowthOpen] = useState(false)
  const [cashAmount, setCashAmount] = useState('')
  const [cashNote, setCashNote] = useState('')
  const [relatedHoldingId, setRelatedHoldingId] = useState('')
  const [incomeReinvested, setIncomeReinvested] = useState(false)
  const [lazyExtendOpen, setLazyExtendOpen] = useState(false)
  const [passiveYear, setPassiveYear] = useState(() => new Date().getFullYear())
  const [passiveIncome, setPassiveIncome] = useState<PassiveIncome | null>(null)
  const [passiveLoading, setPassiveLoading] = useState(false)
  const [taxRateInput, setTaxRateInput] = useState('13')
  const [portfolioNameInput, setPortfolioNameInput] = useState('')
  const [calendarOpen, setCalendarOpen] = useState(false)

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

  useEffect(() => {
    if (kind !== 'stock' || !id) {
      setPassiveIncome(null)
      return
    }
    let cancelled = false
    setPassiveLoading(true)
    void portfoliosApi
      .getPassiveIncome(id, passiveYear)
      .then((data) => {
        if (!cancelled) setPassiveIncome(data)
      })
      .catch(() => {
        if (!cancelled) setPassiveIncome(null)
      })
      .finally(() => {
        if (!cancelled) setPassiveLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [kind, id, passiveYear, portfolio?.updatedAt])

  useEffect(() => {
    if (portfolio?.taxRatePercent != null) {
      setTaxRateInput(String(portfolio.taxRatePercent))
    }
    if (portfolio?.name) {
      setPortfolioNameInput(portfolio.name)
    }
  }, [portfolio?.taxRatePercent, portfolio?.name])

  async function onSaveSettings(e: FormEvent) {
    e.preventDefault()
    setPending(true)
    setError(null)
    try {
      if (kind === 'stock') {
        const updated = await portfoliosApi.updatePortfolioSettings(id, {
          name: portfolioNameInput.trim(),
          taxRatePercent: Number(taxRateInput),
        })
        setPortfolio((prev) =>
          prev ? { ...prev, name: updated.name, taxRatePercent: updated.taxRatePercent } : prev,
        )
        const income = await portfoliosApi.getPassiveIncome(id, passiveYear)
        setPassiveIncome(income)
      } else {
        const updated = await portfoliosApi.updatePortfolio(kind, id, {
          name: portfolioNameInput.trim(),
          description: portfolio?.description ?? null,
        })
        setPortfolio(updated)
      }
      setDialog(null)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Не удалось сохранить настройки')
    } finally {
      setPending(false)
    }
  }

  function resetTradeForm() {
    setQuantity('1')
    setUnitPrice('')
    setOccurredOn(todayIso())
    setError(null)
  }

  function resetCashForm() {
    setCashAmount('')
    setCashNote('')
    setRelatedHoldingId('')
    setIncomeReinvested(false)
    setOccurredOn(todayIso())
    setError(null)
  }

  function openCash(type: 'cash-deposit' | 'cash-withdraw' | 'cash-dividend' | 'cash-coupon') {
    resetCashForm()
    const assets = portfolio?.holdings.filter((h) => !h.cash && Number(h.quantity) > 0) ?? []
    if ((type === 'cash-dividend' || type === 'cash-coupon') && assets[0]) {
      setRelatedHoldingId(assets[0].id)
    }
    setDialog({ type })
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

  function onAddClick() {
    if (kind === 'crypto') {
      setDialog({ type: 'add-choice' })
      return
    }
    openAdd()
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
        unitPrice: unitPrice.trim() ? Number(unitPrice) : undefined,
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
      await portfoliosApi.buyHolding(kind, id, dialog.holding.id, {
        quantity: Number(quantity),
        occurredOn,
        unitPrice: unitPrice.trim() ? Number(unitPrice) : undefined,
      })
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
      await portfoliosApi.sellHolding(kind, id, dialog.holding.id, {
        quantity: Number(quantity),
        occurredOn,
        unitPrice: unitPrice.trim() ? Number(unitPrice) : undefined,
      })
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

  async function onCashSubmit(e: FormEvent) {
    e.preventDefault()
    if (!dialog || !dialog.type.startsWith('cash-')) return
    setPending(true)
    setError(null)
    const payload = {
      amount: Number(cashAmount),
      occurredOn,
      note: cashNote.trim() || undefined,
      relatedHoldingId: relatedHoldingId || undefined,
      settleToCash:
        dialog.type === 'cash-dividend' || dialog.type === 'cash-coupon'
          ? !incomeReinvested
          : undefined,
    }
    try {
      if (dialog.type === 'cash-deposit') await portfoliosApi.depositCash(kind, id, payload)
      else if (dialog.type === 'cash-withdraw') await portfoliosApi.withdrawCash(kind, id, payload)
      else if (dialog.type === 'cash-dividend') await portfoliosApi.addDividend(id, payload)
      else if (dialog.type === 'cash-coupon') await portfoliosApi.addCoupon(id, payload)
      setDialog(null)
      await reload()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Не удалось сохранить')
    } finally {
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
  const cashHolding = portfolio.holdings.find((h) => h.cash) ?? null
  const assetHoldings = portfolio.holdings.filter((h) => !h.cash)
  const openHoldings = [...assetHoldings]
    .filter((h) => Number(h.quantity) > 0)
    .sort((a, b) => Number(b.marketValue ?? 0) - Number(a.marketValue ?? 0))
  const closedHoldings = assetHoldings.filter((h) => Number(h.quantity) <= 0)
  const rows = [
    ...(showClosed ? [...openHoldings, ...closedHoldings] : openHoldings),
    ...(cashHolding ? [cashHolding] : []),
  ]
  const incomeAssets = openHoldings
  const invested = portfolio.holdings.reduce<number | null>((sum, h) => {
    if (Number(h.quantity) <= 0 || h.costBasis == null) return sum
    return (sum ?? 0) + Number(h.costBasis)
  }, null)

  return (
    <div className="page page-wide">
      <p className="crumb">
        <Link to={listPath}>{title}</Link> / {portfolio.name}
      </p>

      <div className="page-head">
        <div>
          <h1 className="page-title page-title-with-icon">
            <span
              className="page-mode-icon"
              title={portfolioModeLabel(portfolio.entryMode || 'MANUAL')}
              aria-label={portfolioModeLabel(portfolio.entryMode || 'MANUAL')}
            >
              <PortfolioModeIcon mode={portfolio.entryMode || 'MANUAL'} size={22} />
            </span>
            {portfolio.name}
            <button
              type="button"
              className="page-settings-star"
              title="Настройки портфеля"
              aria-label="Настройки портфеля"
              onClick={() => {
                setPortfolioNameInput(portfolio.name)
                setTaxRateInput(String(portfolio.taxRatePercent ?? 13))
                setDialog({ type: 'settings' })
              }}
            >
              <GearIcon />
            </button>
          </h1>
          {portfolio.description ? <p className="page-lead">{portfolio.description}</p> : null}
        </div>
        <div className="row" style={{ gap: '0.55rem' }}>
          <button type="button" className="btn btn-ghost" onClick={() => setGrowthOpen(true)}>
            График роста
          </button>
          {kind === 'stock' ? (
            <button type="button" className="btn btn-ghost" onClick={() => setCalendarOpen(true)}>
              Календарь выплат
            </button>
          ) : null}
        </div>
      </div>

      {error && !dialog && <p className="error">{error}</p>}

      <div className={`pf-metrics${kind === 'stock' ? ' pf-metrics--3' : ''}`}>
        <section className="pf-metric">
          <h2 className="pf-metric-title">Стоимость</h2>
          <p className="pf-metric-value">{formatMoney(portfolio.totalValue, currency)}</p>
          <p className="pf-metric-sub">
            <span>Вложено</span>
            <strong>{formatMoney(invested, currency)}</strong>
          </p>
        </section>

        <section className="pf-metric">
          <h2 className="pf-metric-title">Прибыль</h2>
          <p className={`pf-metric-value ${changeClass(portfolio.totalChangeAbs)}`}>
            {formatMoney(portfolio.totalChangeAbs, currency)}
            <span className="pf-metric-pct">{formatPct(portfolio.totalChangePct)}</span>
          </p>
          <p className="pf-metric-sub">
            <span>За сегодня</span>
            <strong className={`pf-metric-day ${changeClass(portfolio.dayChangeAbs)}`}>
              <span>{formatMoney(portfolio.dayChangeAbs, currency)}</span>
              <span className={`pf-day-pct ${changeClass(portfolio.dayChangePct)}`}>
                {formatPct(portfolio.dayChangePct)}
              </span>
            </strong>
          </p>
        </section>

        {kind === 'stock' ? (
          <section className="pf-metric pf-metric--passive">
            <div className="pf-metric-head">
              <h2 className="pf-metric-title">Пассивный доход</h2>
              <div className="pf-year-switch" aria-label="Год прогноза">
                <button
                  type="button"
                  className="pf-year-btn"
                  aria-label="Предыдущий год"
                  onClick={() => setPassiveYear((y) => y - 1)}
                >
                  ‹
                </button>
                <span className="pf-year-value">{passiveYear}</span>
                <button
                  type="button"
                  className="pf-year-btn"
                  aria-label="Следующий год"
                  onClick={() => setPassiveYear((y) => y + 1)}
                >
                  ›
                </button>
              </div>
            </div>
            <p className="pf-metric-value">
              {passiveLoading && !passiveIncome
                ? '…'
                : formatMoney(passiveIncome?.annualNet ?? 0, currency)}
              {passiveIncome && portfolio.totalValue != null && Number(portfolio.totalValue) > 0 ? (
                <span className="pf-metric-pct">
                  {formatPct((Number(passiveIncome.annualNet) / Number(portfolio.totalValue)) * 100)}
                </span>
              ) : null}
            </p>
            <p className="pf-metric-sub">
              <span>В месяц</span>
              <strong>{formatMoney(passiveIncome?.monthlyNet ?? 0, currency)}</strong>
            </p>
          </section>
        ) : null}
      </div>

      <section className="panel table-panel">
        <div className="section-title-row">
          <h2>Позиции</h2>
          <div className="row" style={{ gap: '0.5rem', flexWrap: 'wrap' }}>
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
            <button type="button" className="icon-btn" aria-label="Добавить актив" onClick={onAddClick}>
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
                  <th>За день</th>
                  <th>Прибыль</th>
                  <th>Доля</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {rows.map((h) => {
                  const isCash = Boolean(h.cash)
                  const closed = !isCash && Number(h.quantity) <= 0
                  const cur = h.currency || currency
                  const avg = avgPrice(h)
                  const share = sharePct(h, portfolio.totalValue)
                  const income = Number(h.incomeAbs ?? 0)
                  const ret = totalReturn(h)
                  const retPct =
                    ret != null && h.costBasis != null && Number(h.costBasis) > 0
                      ? (ret / Number(h.costBasis)) * 100
                      : isCash
                        ? h.totalChangePct
                        : income > 0
                          ? null
                          : h.totalChangePct
                  const showIncomeTip = !isCash && income > 0
                  return (
                    <tr key={h.id} className={[closed ? 'closed' : '', isCash ? 'cash-row' : ''].filter(Boolean).join(' ') || undefined}>
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
                        {isCash ? (
                          <div className="cell-main">—</div>
                        ) : (
                          <>
                            <div className="cell-main">{h.quantity}</div>
                            <div className="cell-sub">шт</div>
                          </>
                        )}
                      </td>
                      <td>
                        {isCash ? (
                          <div className="cell-main">—</div>
                        ) : (
                          <>
                            <div className="cell-main">{formatMoney(h.costBasis, cur)}</div>
                            <div className="cell-sub">{formatMoney(avg, cur)} / шт</div>
                          </>
                        )}
                      </td>
                      <td>
                        <div className="cell-main">{formatMoney(h.marketValue, cur)}</div>
                        {!isCash ? <div className="cell-sub">{formatMoney(h.unitPrice, cur)} / шт</div> : null}
                      </td>
                      <td>
                        {isCash ? (
                          <div className="cell-main">—</div>
                        ) : (
                          <>
                            <div className={`cell-main ${changeClass(h.dayChangeAbs)}`}>
                              {formatMoney(h.dayChangeAbs, cur)}
                            </div>
                            <div className={`cell-sub ${changeClass(h.dayChangePct)}`}>
                              {formatPct(h.dayChangePct)}
                            </div>
                          </>
                        )}
                      </td>
                      <td>
                        {isCash ? (
                          <div className="cell-main">—</div>
                        ) : (
                          <>
                            <div className="cell-main pnl-tip">
                              <span className={changeClass(ret)}>{formatMoney(ret, cur)}</span>
                              {showIncomeTip ? (
                                <span className="pnl-tip-box" role="tooltip">
                                  <div>
                                    От цены:{' '}
                                    <span className={changeClass(pricePnl(h))}>{formatMoney(pricePnl(h), cur)}</span>
                                  </div>
                                  <div>
                                    Дивиденды/купоны:{' '}
                                    <span className={changeClass(income)}>{formatMoney(income, cur)}</span>
                                  </div>
                                  <div className="pnl-tip-total">
                                    Всего: <span className={changeClass(ret)}>{formatMoney(ret, cur)}</span>
                                  </div>
                                </span>
                              ) : null}
                            </div>
                            <div className={`cell-sub ${changeClass(retPct)}`}>{formatPct(retPct)}</div>
                          </>
                        )}
                      </td>
                      <td>
                        <div className="cell-main">{share == null ? '—' : `${share.toFixed(1)}%`}</div>
                        <div className="cell-sub">портфеля</div>
                      </td>
                      <td>
                        <span className="holding-actions">
                          {isCash ? (
                            <>
                              <IconButton label="Пополнить" onClick={() => setDialog({ type: 'cash-in' })}>
                                <PlusIcon />
                              </IconButton>
                              <IconButton label="Вывести" onClick={() => openCash('cash-withdraw')}>
                                <MinusIcon />
                              </IconButton>
                            </>
                          ) : (
                            <>
                              <IconButton
                                label="Купить"
                                onClick={() => {
                                  resetTradeForm()
                                  setDialog({ type: 'buy', holding: h })
                                }}
                              >
                                <PlusIcon />
                              </IconButton>
                              <IconButton
                                label="Продать"
                                onClick={() => {
                                  resetTradeForm()
                                  setDialog({ type: 'sell', holding: h })
                                }}
                              >
                                <MinusIcon />
                              </IconButton>
                            </>
                          )}
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

      <Modal open={dialog?.type === 'add-choice'} title="Добавить в портфель" onClose={() => setDialog(null)}>
        <div className="stack cash-in-choices">
          <button
            type="button"
            className="cash-in-choice"
            onClick={() => {
              setDialog(null)
              openAdd()
            }}
          >
            <strong>Классически</strong>
            <span>Открыть позицию с количеством и ценой покупки</span>
          </button>
          <button
            type="button"
            className="cash-in-choice"
            onClick={() => {
              setDialog(null)
              setLazyExtendOpen(true)
            }}
          >
            <strong>Дополнить ленивый ввод</strong>
            <span>Добавить активы, изменить вложенное и пересчитать средние цены</span>
          </button>
          <div className="modal-actions">
            <button type="button" className="btn btn-ghost" onClick={() => setDialog(null)}>
              Отмена
            </button>
          </div>
        </div>
      </Modal>

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
            <label htmlFor="unitPrice">Цена за единицу (необязательно)</label>
            <input
              id="unitPrice"
              type="number"
              step="any"
              min="0"
              placeholder="Если пусто — возьмём рыночную"
              value={unitPrice}
              onChange={(e) => setUnitPrice(e.target.value)}
            />
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
            <label htmlFor="buy-price">Цена за единицу (необязательно)</label>
            <input
              id="buy-price"
              type="number"
              step="any"
              min="0"
              placeholder="Если пусто — возьмём рыночную"
              value={unitPrice}
              onChange={(e) => setUnitPrice(e.target.value)}
            />
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
            <label htmlFor="sell-price">Цена за единицу (необязательно)</label>
            <input
              id="sell-price"
              type="number"
              step="any"
              min="0"
              placeholder="Если пусто — возьмём рыночную"
              value={unitPrice}
              onChange={(e) => setUnitPrice(e.target.value)}
            />
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
            {trades.map((t) => {
              const cashHistory = dialog?.type === 'history' && Boolean(dialog.holding.cash)
              return (
              <li key={t.id} className="trade-row">
                <span className={`trade-side ${txKindClass(t)}`}>{txKindLabel(t)}</span>
                <span className="trade-qty">
                  {cashHistory ? formatMoney(t.quantity, currency) : t.quantity}
                </span>
                <span className="trade-date">
                  {t.occurredOn}
                  {t.unitPrice != null && !cashHistory ? ` · ${formatMoney(t.unitPrice, currency)}` : ''}
                  {t.note ? ` · ${t.note}` : ''}
                </span>
              </li>
              )
            })}
          </ul>
        )}
        <div className="modal-actions">
          <button type="button" className="btn btn-ghost" onClick={() => setDialog(null)}>Закрыть</button>
        </div>
      </Modal>

      <Modal open={dialog?.type === 'cash-in'} title="Пополнить валюту" onClose={() => setDialog(null)}>
        <div className="stack cash-in-choices">
          <button type="button" className="cash-in-choice" onClick={() => openCash('cash-deposit')}>
            <strong>Пополнение</strong>
            <span>Депозит — увеличивает вложенное</span>
          </button>
          {kind === 'stock' ? (
            <>
              <button type="button" className="cash-in-choice" onClick={() => openCash('cash-dividend')}>
                <strong>Дивиденд</strong>
                <span>Прибыль от компании из портфеля</span>
              </button>
              <button type="button" className="cash-in-choice" onClick={() => openCash('cash-coupon')}>
                <strong>Купон</strong>
                <span>Прибыль по облигации из портфеля</span>
              </button>
            </>
          ) : null}
          <div className="modal-actions">
            <button type="button" className="btn btn-ghost" onClick={() => setDialog(null)}>
              Отмена
            </button>
          </div>
        </div>
      </Modal>

      <Modal
        open={
          dialog?.type === 'cash-deposit' ||
          dialog?.type === 'cash-withdraw' ||
          dialog?.type === 'cash-dividend' ||
          dialog?.type === 'cash-coupon'
        }
        title={
          dialog?.type === 'cash-deposit'
            ? 'Пополнить валюту'
            : dialog?.type === 'cash-withdraw'
              ? 'Вывести валюту'
              : dialog?.type === 'cash-dividend'
                ? 'Дивиденд'
                : dialog?.type === 'cash-coupon'
                  ? 'Купон'
                  : 'Валюта'
        }
        onClose={() => !pending && setDialog(null)}
      >
        <form className="stack" onSubmit={onCashSubmit}>
          {(dialog?.type === 'cash-dividend' || dialog?.type === 'cash-coupon') && (
            <div className="field">
              <label htmlFor="related-holding">Компания</label>
              <select
                id="related-holding"
                required
                value={relatedHoldingId}
                onChange={(e) => setRelatedHoldingId(e.target.value)}
              >
                <option value="" disabled>
                  Выберите актив
                </option>
                {incomeAssets.map((h) => (
                  <option key={h.id} value={h.id}>
                    {h.symbol} — {h.name || h.symbol}
                  </option>
                ))}
              </select>
              {incomeAssets.length === 0 ? (
                <p className="muted" style={{ marginBottom: 0 }}>
                  Сначала добавьте акции или облигации в портфель
                </p>
              ) : null}
            </div>
          )}
          <div className="field">
            <label htmlFor="cash-amount">Сумма ({currency})</label>
            <input
              id="cash-amount"
              type="number"
              step="any"
              min="0"
              required
              autoFocus
              value={cashAmount}
              onChange={(e) => setCashAmount(e.target.value)}
            />
          </div>
          <div className="field">
            <label htmlFor="cash-date">Дата</label>
            <input id="cash-date" type="date" required value={occurredOn} onChange={(e) => setOccurredOn(e.target.value)} />
          </div>
          <div className="field">
            <label htmlFor="cash-note">Комментарий</label>
            <input id="cash-note" value={cashNote} onChange={(e) => setCashNote(e.target.value)} placeholder="необязательно" />
          </div>
          {dialog?.type === 'cash-deposit' ? (
            <p className="muted" style={{ marginTop: 0 }}>
              Пополнение увеличивает вложенное.
            </p>
          ) : null}
          {dialog?.type === 'cash-dividend' || dialog?.type === 'cash-coupon' ? (
            <>
              <label className="field-check">
                <input
                  type="checkbox"
                  checked={incomeReinvested}
                  onChange={(e) => setIncomeReinvested(e.target.checked)}
                />
                <span>
                  Реинвест / потрачены — не зачислять на валюту, только в прибыль актива
                </span>
              </label>
              <p className="muted" style={{ marginTop: 0 }}>
                {incomeReinvested
                  ? 'Сумма учтётся у компании, баланс RUB не изменится.'
                  : 'Сумма поступит на валюту и будет учтена у выбранной компании.'}
              </p>
            </>
          ) : null}
          {error && dialog?.type?.startsWith('cash-') && <p className="error">{error}</p>}
          <div className="modal-actions">
            <button type="button" className="btn btn-ghost" disabled={pending} onClick={() => setDialog(null)}>
              Отмена
            </button>
            <button
              type="submit"
              className="btn"
              disabled={
                pending ||
                ((dialog?.type === 'cash-dividend' || dialog?.type === 'cash-coupon') &&
                  (!relatedHoldingId || incomeAssets.length === 0))
              }
            >
              {pending ? 'Сохраняем…' : 'Сохранить'}
            </button>
          </div>
        </form>
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

      <Modal open={dialog?.type === 'settings'} title="Настройки портфеля" onClose={() => !pending && setDialog(null)}>
        <form className="stack" onSubmit={onSaveSettings}>
          <div className="field">
            <label htmlFor="pf-name">Название</label>
            <input
              id="pf-name"
              required
              maxLength={120}
              value={portfolioNameInput}
              onChange={(e) => setPortfolioNameInput(e.target.value)}
            />
          </div>
          {kind === 'stock' ? (
            <>
              <div className="field">
                <label htmlFor="tax-rate">Налог на дивиденды и купоны, %</label>
                <input
                  id="tax-rate"
                  type="number"
                  step="0.1"
                  min="0"
                  max="100"
                  required
                  value={taxRateInput}
                  onChange={(e) => setTaxRateInput(e.target.value)}
                />
              </div>
              <p className="muted" style={{ marginTop: 0 }}>
                Прогноз пассивного дохода показывается после налога. На уже записанные выплаты не влияет.
              </p>
            </>
          ) : null}
          {error && dialog?.type === 'settings' && <p className="error">{error}</p>}
          <div className="modal-actions">
            <button type="button" className="btn btn-ghost" disabled={pending} onClick={() => setDialog(null)}>
              Отмена
            </button>
            <button type="submit" className="btn" disabled={pending}>
              {pending ? 'Сохраняем…' : 'Сохранить'}
            </button>
          </div>
          <div className="settings-danger">
            <button
              type="button"
              className="btn btn-danger"
              disabled={pending}
              onClick={() => setDialog({ type: 'deletePortfolio' })}
            >
              Удалить портфель
            </button>
          </div>
        </form>
      </Modal>

      <GrowthChartModal
        open={growthOpen}
        kind={kind}
        portfolioId={portfolio.id}
        portfolioName={portfolio.name}
        currency={currency}
        onClose={() => setGrowthOpen(false)}
      />

      {kind === 'stock' ? (
        <PaymentCalendarModal
          open={calendarOpen}
          portfolioId={portfolio.id}
          portfolioName={portfolio.name}
          currency={currency}
          onClose={() => setCalendarOpen(false)}
        />
      ) : null}

      {kind === 'crypto' ? (
        <LazySeedModal
          open={lazyExtendOpen}
          kind="crypto"
          portfolioId={portfolio.id}
          mode="extend"
          existingHoldings={portfolio.holdings}
          onClose={() => setLazyExtendOpen(false)}
          onDone={() => {
            setLazyExtendOpen(false)
            void reload()
          }}
        />
      ) : null}
    </div>
  )
}
