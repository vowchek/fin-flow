import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { ApiError } from '../../api/client'
import * as instrumentsApi from '../../api/instruments'
import * as portfoliosApi from '../../api/portfolios'
import type { Holding, Instrument, PortfolioKind } from '../../api/types'
import { AssetLogo } from '../../components/AssetLogo'
import { Modal } from '../../components/Modal'

type StockRow = {
  key: string
  instrument: Instrument
  quantity: string
  unitPrice: string
  occurredOn: string
}

type CryptoRow = {
  key: string
  instrument: Instrument
  quantity: string
}

type Props = {
  open: boolean
  kind: PortfolioKind
  portfolioId: string
  mode?: 'create' | 'extend'
  /** Prefill for crypto extend / edit lazy allocation */
  existingHoldings?: Holding[]
  onClose: () => void
  onDone: () => void
}

function todayIso() {
  return new Date().toISOString().slice(0, 10)
}

function newKey() {
  return `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
}

export function LazySeedModal({
  open,
  kind,
  portfolioId,
  mode = 'create',
  existingHoldings,
  onClose,
  onDone,
}: Props) {
  const [error, setError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)
  const [catalog, setCatalog] = useState<Instrument[]>([])
  const [catalogLoading, setCatalogLoading] = useState(false)
  const [query, setQuery] = useState('')

  const [stockRows, setStockRows] = useState<StockRow[]>([])
  const [cryptoRows, setCryptoRows] = useState<CryptoRow[]>([])
  const [investedAmount, setInvestedAmount] = useState('')
  const [startedOn, setStartedOn] = useState(todayIso())

  const isExtend = mode === 'extend' && kind === 'crypto'

  useEffect(() => {
    if (!open) return
    setError(null)
    setPending(false)
    setQuery('')
    setStockRows([])
    setCryptoRows([])
    setInvestedAmount('')
    setStartedOn(todayIso())
    setCatalogLoading(true)
    void instrumentsApi
      .listCatalog(kind)
      .then((items) => {
        setCatalog(items)
        if (kind !== 'crypto') return
        const assets = (existingHoldings ?? []).filter((h) => !h.cash && Number(h.quantity) > 0)
        if (assets.length === 0) return
        const bySymbol = new Map(items.map((i) => [i.symbol.toUpperCase(), i]))
        const rows: CryptoRow[] = []
        for (const h of assets) {
          const instrument = bySymbol.get(h.symbol.toUpperCase())
          if (!instrument) continue
          rows.push({ key: newKey(), instrument, quantity: String(h.quantity) })
        }
        setCryptoRows(rows)
        if (mode === 'extend') {
          const invested = assets.reduce((sum, h) => sum + Number(h.costBasis ?? 0), 0)
          if (invested > 0) setInvestedAmount(String(Math.round(invested * 100) / 100))
          const dates = assets.map((h) => h.openedOn).filter(Boolean) as string[]
          if (dates.length > 0) {
            setStartedOn([...dates].sort()[0]!)
          }
        }
      })
      .catch(() => setCatalog([]))
      .finally(() => setCatalogLoading(false))
  }, [open, kind, mode])

  const usedSymbols = useMemo(() => {
    const rows = kind === 'stock' ? stockRows : cryptoRows
    return new Set(rows.map((r) => r.instrument.symbol.toUpperCase()))
  }, [kind, stockRows, cryptoRows])

  const filteredCatalog = (() => {
    const q = query.trim().toLowerCase()
    if (!q) return []
    return catalog.filter(
      (item) =>
        !usedSymbols.has(item.symbol.toUpperCase()) &&
        (item.symbol.toLowerCase().includes(q) ||
          item.name.toLowerCase().includes(q) ||
          item.externalId.toLowerCase().includes(q)),
    )
  })()

  function addInstrument(item: Instrument) {
    setQuery('')
    if (kind === 'stock') {
      setStockRows((rows) => [
        ...rows,
        {
          key: newKey(),
          instrument: item,
          quantity: '1',
          unitPrice: '',
          occurredOn: todayIso(),
        },
      ])
    } else {
      setCryptoRows((rows) => [
        ...rows,
        {
          key: newKey(),
          instrument: item,
          quantity: '1',
        },
      ])
    }
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    setPending(true)
    setError(null)
    try {
      if (kind === 'stock') {
        if (stockRows.length === 0) {
          setError('Добавьте хотя бы один актив')
          setPending(false)
          return
        }
        for (const row of stockRows) {
          if (!row.quantity || Number(row.quantity) <= 0) {
            setError(`Укажите количество для ${row.instrument.symbol}`)
            setPending(false)
            return
          }
          if (!row.unitPrice || Number(row.unitPrice) <= 0) {
            setError(`Укажите среднюю цену для ${row.instrument.symbol}`)
            setPending(false)
            return
          }
          if (!row.occurredOn) {
            setError(`Укажите дату первой покупки для ${row.instrument.symbol}`)
            setPending(false)
            return
          }
        }
        if (!investedAmount || Number(investedAmount) <= 0) {
          setError('Укажите сумму вложений')
          setPending(false)
          return
        }
        await portfoliosApi.seedLazyStock(portfolioId, {
          investedAmount: Number(investedAmount),
          holdings: stockRows.map((row) => ({
            instrumentId: row.instrument.id,
            symbol: row.instrument.symbol,
            name: row.instrument.name,
            quantity: Number(row.quantity),
            occurredOn: row.occurredOn,
            unitPrice: Number(row.unitPrice),
          })),
        })
      } else {
        if (!investedAmount || Number(investedAmount) <= 0) {
          setError('Укажите вложенную сумму')
          setPending(false)
          return
        }
        if (!startedOn) {
          setError('Укажите дату начала учёта')
          setPending(false)
          return
        }
        if (cryptoRows.length === 0) {
          setError('Добавьте хотя бы один актив')
          setPending(false)
          return
        }
        for (const row of cryptoRows) {
          if (!row.quantity || Number(row.quantity) <= 0) {
            setError(`Укажите количество для ${row.instrument.symbol}`)
            setPending(false)
            return
          }
        }
        await portfoliosApi.seedLazyCrypto(portfolioId, {
          investedAmount: Number(investedAmount),
          startedOn,
          holdings: cryptoRows.map((row) => ({
            instrumentId: row.instrument.id,
            symbol: row.instrument.symbol,
            quantity: Number(row.quantity),
          })),
        })
      }
      onDone()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Не удалось сохранить первый ввод')
    } finally {
      setPending(false)
    }
  }

  const currency = kind === 'stock' ? '₽' : '$'
  const title = isExtend ? 'Дополнить ленивый ввод' : 'Первый ввод позиций'

  return (
    <Modal open={open} wide title={title} onClose={() => !pending && onClose()}>
      <form className="stack seed-form" onSubmit={onSubmit}>
        <p className="muted seed-lead">
          {kind === 'stock'
            ? 'Укажите, сколько реально вложили денег, затем активы со средней ценой и датой — как в кабинете брокера. Дивиденды потом учитываются отдельно в прибыли.'
            : isExtend
              ? 'Добавьте активы или измените количества и сумму вложений. Средние цены входа пересчитаем по текущим долям рыночной стоимости — текущие ленивые позиции будут заменены.'
              : 'Укажите, сколько всего вложили и когда начали вести портфель, затем количества активов. Среднюю цену входа посчитаем по текущим долям стоимости.'}
        </p>

        <div className="seed-meta">
          <div className="field">
            <label htmlFor="invested">Вложено ({currency})</label>
            <input
              id="invested"
              type="number"
              step="any"
              min="0"
              required
              value={investedAmount}
              onChange={(e) => setInvestedAmount(e.target.value)}
              placeholder={kind === 'stock' ? 'Сколько денег внесли' : undefined}
            />
          </div>
          {kind === 'crypto' ? (
            <div className="field">
              <label htmlFor="started">Дата начала учёта</label>
              <input
                id="started"
                type="date"
                required
                value={startedOn}
                onChange={(e) => setStartedOn(e.target.value)}
              />
            </div>
          ) : null}
        </div>

        <div className="field">
          <label htmlFor="seed-search">Добавить актив</label>
          <input
            id="seed-search"
            placeholder="Тикер или название…"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
          {catalogLoading ? (
            <p className="muted">Загрузка каталога…</p>
          ) : !query.trim() ? null : filteredCatalog.length === 0 ? (
            <p className="muted">{catalog.length === 0 ? 'Каталог пуст' : 'Ничего не найдено'}</p>
          ) : (
            <ul className="suggest-list">
              {filteredCatalog.slice(0, 8).map((item) => (
                <li key={item.id}>
                  <button type="button" className="suggest-item" onClick={() => addInstrument(item)}>
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
        </div>

        {kind === 'stock' ? (
          stockRows.length === 0 ? (
            <p className="muted">Пока нет активов — найдите и добавьте из каталога</p>
          ) : (
            <ul className="seed-rows">
              {stockRows.map((row) => (
                <li key={row.key} className="seed-row">
                  <div className="seed-row-asset">
                    <AssetLogo
                      symbol={row.instrument.symbol}
                      name={row.instrument.name}
                      logoUrl={row.instrument.logoUrl}
                      size={28}
                    />
                    <div>
                      <strong>{row.instrument.symbol}</strong>
                      <span className="holding-meta">{row.instrument.name}</span>
                    </div>
                    <button
                      type="button"
                      className="btn btn-ghost btn-sm"
                      onClick={() => setStockRows((rows) => rows.filter((r) => r.key !== row.key))}
                    >
                      Убрать
                    </button>
                  </div>
                  <div className="seed-row-fields">
                    <div className="field">
                      <label>Количество</label>
                      <input
                        type="number"
                        step="any"
                        min="0"
                        required
                        value={row.quantity}
                        onChange={(e) =>
                          setStockRows((rows) =>
                            rows.map((r) => (r.key === row.key ? { ...r, quantity: e.target.value } : r)),
                          )
                        }
                      />
                    </div>
                    <div className="field">
                      <label>Средняя цена ({currency})</label>
                      <input
                        type="number"
                        step="any"
                        min="0"
                        required
                        value={row.unitPrice}
                        onChange={(e) =>
                          setStockRows((rows) =>
                            rows.map((r) => (r.key === row.key ? { ...r, unitPrice: e.target.value } : r)),
                          )
                        }
                      />
                    </div>
                    <div className="field">
                      <label>Первая покупка</label>
                      <input
                        type="date"
                        required
                        value={row.occurredOn}
                        onChange={(e) =>
                          setStockRows((rows) =>
                            rows.map((r) => (r.key === row.key ? { ...r, occurredOn: e.target.value } : r)),
                          )
                        }
                      />
                    </div>
                  </div>
                </li>
              ))}
            </ul>
          )
        ) : cryptoRows.length === 0 ? (
          <p className="muted">Пока нет активов — найдите и добавьте из каталога</p>
        ) : (
          <ul className="seed-rows">
            {cryptoRows.map((row) => (
              <li key={row.key} className="seed-row">
                <div className="seed-row-asset">
                  <AssetLogo
                    symbol={row.instrument.symbol}
                    name={row.instrument.name}
                    logoUrl={row.instrument.logoUrl}
                    size={28}
                  />
                  <div>
                    <strong>{row.instrument.symbol}</strong>
                    <span className="holding-meta">{row.instrument.name}</span>
                  </div>
                  <button
                    type="button"
                    className="btn btn-ghost btn-sm"
                    onClick={() => setCryptoRows((rows) => rows.filter((r) => r.key !== row.key))}
                  >
                    Убрать
                  </button>
                </div>
                <div className="seed-row-fields seed-row-fields--crypto">
                  <div className="field">
                    <label>Количество</label>
                    <input
                      type="number"
                      step="any"
                      min="0"
                      required
                      value={row.quantity}
                      onChange={(e) =>
                        setCryptoRows((rows) =>
                          rows.map((r) => (r.key === row.key ? { ...r, quantity: e.target.value } : r)),
                        )
                      }
                    />
                  </div>
                </div>
              </li>
            ))}
          </ul>
        )}

        {error ? <p className="error">{error}</p> : null}

        <div className="modal-actions">
          <button type="button" className="btn btn-ghost" disabled={pending} onClick={onClose}>
            {isExtend ? 'Отмена' : 'Позже'}
          </button>
          <button type="submit" className="btn" disabled={pending}>
            {pending ? 'Сохраняем…' : isExtend ? 'Пересчитать портфель' : 'Сохранить ввод'}
          </button>
        </div>
      </form>
    </Modal>
  )
}
