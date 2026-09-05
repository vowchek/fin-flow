import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { ApiError } from '../../api/client'
import * as instrumentsApi from '../../api/instruments'
import type { InstrumentPayment, RemoteInstrument } from '../../api/instruments'
import type { Instrument, PortfolioKind } from '../../api/types'
import { AssetLogo } from '../../components/AssetLogo'
import { Modal } from '../../components/Modal'
import { formatMoney } from '../../lib/format'

type Draft = {
  symbol: string
  externalId: string
  name: string
  currency: string
  enabled: boolean
  assetKind: 'EQUITY' | 'BOND'
  paysDividends: boolean
}

type FormTab = 'import' | 'main' | 'payments'

const emptyDraft = (kind: PortfolioKind): Draft => ({
  symbol: '',
  externalId: '',
  name: '',
  currency: kind === 'crypto' ? 'USD' : 'RUB',
  enabled: true,
  assetKind: 'EQUITY',
  paysDividends: true,
})

function draftFromInstrument(item: Instrument): Draft {
  return {
    symbol: item.symbol,
    externalId: item.externalId,
    name: item.name,
    currency: item.currency,
    enabled: item.enabled,
    assetKind: item.assetKind === 'BOND' ? 'BOND' : 'EQUITY',
    paysDividends: item.paysDividends !== false,
  }
}

function paymentKindLabel(kind: InstrumentPayment['kind']) {
  return kind === 'COUPON' ? 'Купон' : 'Дивиденд'
}

export function AdminCatalogPage() {
  const [kind, setKind] = useState<PortfolioKind>('stock')
  const [items, setItems] = useState<Instrument[]>([])
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [editing, setEditing] = useState<Instrument | null>(null)
  const [creating, setCreating] = useState(false)
  const [draft, setDraft] = useState<Draft>(() => emptyDraft('stock'))
  const [pending, setPending] = useState(false)
  const [logoFile, setLogoFile] = useState<File | null>(null)
  const [tab, setTab] = useState<FormTab>('main')
  const [searchQ, setSearchQ] = useState('')
  const [searchResults, setSearchResults] = useState<RemoteInstrument[]>([])
  const [searchLoading, setSearchLoading] = useState(false)
  const [payments, setPayments] = useState<InstrumentPayment[]>([])
  const [paymentsLoading, setPaymentsLoading] = useState(false)
  const [bulkMsg, setBulkMsg] = useState<string | null>(null)

  const title = kind === 'stock' ? 'Фонд (MOEX)' : 'Крипта (CoinGecko)'

  async function reload() {
    setLoading(true)
    setError(null)
    try {
      setItems(await instrumentsApi.listAdminCatalog(kind))
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Ошибка загрузки')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void reload()
  }, [kind])

  useEffect(() => {
    if (kind !== 'stock' || !searchQ.trim() || searchQ.trim().length < 1) {
      setSearchResults([])
      return
    }
    const handle = window.setTimeout(() => {
      setSearchLoading(true)
      instrumentsApi
        .searchMoex(searchQ)
        .then(setSearchResults)
        .catch(() => setSearchResults([]))
        .finally(() => setSearchLoading(false))
    }, 280)
    return () => window.clearTimeout(handle)
  }, [searchQ, kind])

  useEffect(() => {
    if (!editing || kind !== 'stock' || tab !== 'payments') return
    setPaymentsLoading(true)
    instrumentsApi
      .listInstrumentPayments(editing.id)
      .then(setPayments)
      .catch(() => setPayments([]))
      .finally(() => setPaymentsLoading(false))
  }, [editing, kind, tab])

  function openCreate() {
    setEditing(null)
    setDraft(emptyDraft(kind))
    setLogoFile(null)
    setTab(kind === 'stock' ? 'import' : 'main')
    setSearchQ('')
    setSearchResults([])
    setPayments([])
    setCreating(true)
    setError(null)
  }

  function openEdit(item: Instrument) {
    setCreating(false)
    setEditing(item)
    setDraft(draftFromInstrument(item))
    setLogoFile(null)
    setTab('main')
    setSearchQ('')
    setSearchResults([])
    setPayments([])
    setError(null)
  }

  function closeModal() {
    if (pending) return
    setCreating(false)
    setEditing(null)
    setTab('main')
    setSearchQ('')
    setSearchResults([])
    setPayments([])
  }

  function applyImported(result: instrumentsApi.InstrumentImportResult) {
    setEditing(result.instrument)
    setCreating(false)
    setDraft(draftFromInstrument(result.instrument))
    setPayments(result.payments)
    setTab('main')
    setSearchQ('')
    setSearchResults([])
  }

  async function onImportRemote(remote: RemoteInstrument) {
    setPending(true)
    setError(null)
    try {
      const result = await instrumentsApi.importFromMoex({
        externalId: remote.externalId,
        symbol: remote.symbol,
        enabled: draft.enabled,
        overwriteCashflow: true,
      })
      applyImported(result)
      await reload()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Не удалось импортировать')
    } finally {
      setPending(false)
    }
  }

  async function onRefreshMoex() {
    if (!editing) return
    setPending(true)
    setError(null)
    try {
      const result = await instrumentsApi.refreshFromMoex(editing.id, true)
      applyImported(result)
      await reload()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Не удалось обновить с T‑Invest')
    } finally {
      setPending(false)
    }
  }

  async function onRefreshAllPayments() {
    if (kind !== 'stock') return
    if (!window.confirm(`Обновить дивиденды/купоны для всех ${items.length} активов? Это может занять минуту.`)) {
      return
    }
    setPending(true)
    setError(null)
    setBulkMsg(null)
    try {
      const result = await instrumentsApi.refreshAllPayments()
      setBulkMsg(
        `Выплаты обновлены: ${result.refreshed} ок` +
          (result.failed > 0 ? `, ${result.failed} ошибок` : ''),
      )
      await reload()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Не удалось массово обновить выплаты')
    } finally {
      setPending(false)
    }
  }

  async function onSave(e: FormEvent) {
    e.preventDefault()
    setPending(true)
    setError(null)
    try {
      const payload = {
        symbol: draft.symbol,
        externalId: draft.externalId,
        name: draft.name,
        currency: draft.currency,
        enabled: draft.enabled,
        assetKind: kind === 'stock' ? draft.assetKind : null,
        paysDividends: kind === 'stock' ? draft.paysDividends : null,
      }
      let saved: Instrument
      if (editing) {
        saved = await instrumentsApi.updateInstrument(kind, editing.id, payload)
      } else {
        saved = await instrumentsApi.createInstrument(kind, payload)
      }
      if (logoFile) {
        saved = await instrumentsApi.uploadLogo(kind, saved.id, logoFile)
      }
      setCreating(false)
      setEditing(null)
      setLogoFile(null)
      setTab('main')
      setPayments([])
      await reload()
      void saved
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Не удалось сохранить')
    } finally {
      setPending(false)
    }
  }

  async function onDelete(item: Instrument) {
    if (!window.confirm(`Удалить ${item.symbol}?`)) return
    setPending(true)
    try {
      await instrumentsApi.deleteInstrument(kind, item.id)
      await reload()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Не удалось удалить')
    } finally {
      setPending(false)
    }
  }

  const modalOpen = creating || editing != null
  const hint = useMemo(
    () =>
      kind === 'stock'
        ? 'externalId = SECID Мосбиржи (например SBER)'
        : 'externalId = id CoinGecko (например bitcoin)',
    [kind],
  )

  return (
    <div className="page">
      <div className="page-head">
        <div>
          <h1 className="page-title">Админка каталога</h1>
          <p className="page-lead">Тикеры, которые увидят пользователи при добавлении актива.</p>
        </div>
        <div className="row" style={{ gap: '0.5rem' }}>
          {kind === 'stock' ? (
            <button type="button" className="btn btn-ghost" disabled={pending || items.length === 0} onClick={() => void onRefreshAllPayments()}>
              {pending ? 'Обновляем…' : 'Обновить дивиденды'}
            </button>
          ) : null}
          <button type="button" className="btn" onClick={openCreate}>
            Добавить
          </button>
        </div>
      </div>

      <div className="row" style={{ gap: '0.5rem', marginBottom: '1rem' }}>
        <button type="button" className={`btn ${kind === 'stock' ? '' : 'btn-ghost'}`} onClick={() => setKind('stock')}>
          Фонд
        </button>
        <button type="button" className={`btn ${kind === 'crypto' ? '' : 'btn-ghost'}`} onClick={() => setKind('crypto')}>
          Крипта
        </button>
      </div>

      {bulkMsg && !modalOpen ? <p className="muted">{bulkMsg}</p> : null}
      {error && !modalOpen && <p className="error">{error}</p>}

      {loading ? (
        <div className="panel empty">Загрузка…</div>
      ) : items.length === 0 ? (
        <div className="panel empty">В каталоге «{title}» пока пусто</div>
      ) : (
        <ul className="list">
          {items.map((item) => (
            <li key={item.id} className="holding-row priced">
              <span className="holding-main">
                <AssetLogo symbol={item.symbol} name={item.name} logoUrl={item.logoUrl} />
                <span>
                  <strong>
                    {item.symbol}
                    {!item.enabled ? <span className="holding-badge">выкл</span> : null}
                    {kind === 'stock' && item.paysDividends === false ? (
                      <span className="holding-badge">без дивов</span>
                    ) : null}
                  </strong>
                  <span className="holding-meta">
                    {item.name} · {item.externalId}
                    {kind === 'stock' && item.assetKind === 'BOND' ? ' · облигация' : ''}
                  </span>
                </span>
              </span>
              <span className="holding-figures">
                <span>{item.currency}</span>
              </span>
              <span className="holding-actions">
                <button type="button" className="btn btn-ghost" onClick={() => openEdit(item)}>
                  Изменить
                </button>
                <button type="button" className="btn btn-danger" disabled={pending} onClick={() => void onDelete(item)}>
                  Удалить
                </button>
              </span>
            </li>
          ))}
        </ul>
      )}

      <Modal
        open={modalOpen}
        title={editing ? `Изменить ${editing.symbol}` : `Новый актив · ${title}`}
        onClose={closeModal}
      >
        <form className="stack adm-form" onSubmit={onSave}>
          {kind === 'stock' ? (
            <div className="adm-tabs" role="tablist" aria-label="Разделы карточки актива">
              {creating ? (
                <button
                  type="button"
                  role="tab"
                  aria-selected={tab === 'import'}
                  className={`adm-tab${tab === 'import' ? ' active' : ''}`}
                  onClick={() => setTab('import')}
                >
                  Импорт
                </button>
              ) : null}
              <button
                type="button"
                role="tab"
                aria-selected={tab === 'main'}
                className={`adm-tab${tab === 'main' ? ' active' : ''}`}
                onClick={() => setTab('main')}
              >
                Основное
              </button>
              {editing ? (
                <button
                  type="button"
                  role="tab"
                  aria-selected={tab === 'payments'}
                  className={`adm-tab${tab === 'payments' ? ' active' : ''}`}
                  onClick={() => setTab('payments')}
                >
                  Выплаты
                </button>
              ) : null}
            </div>
          ) : null}

          {kind === 'stock' && creating ? (
            <div className={`adm-panel stack${tab === 'import' ? '' : ' adm-panel--hidden'}`} role="tabpanel">
              <p className="muted" style={{ marginTop: 0 }}>
                Найдите бумагу в T‑Invest — карточка и история дивидендов/купонов заполнятся сами.
              </p>
              <div className="field">
                <label htmlFor="adm-moex-q">Поиск T‑Invest</label>
                <input
                  id="adm-moex-q"
                  value={searchQ}
                  onChange={(e) => setSearchQ(e.target.value)}
                  placeholder="SBER, Газпром…"
                  autoComplete="off"
                />
              </div>
              {searchLoading ? <p className="muted">Ищем…</p> : null}
              {!searchLoading && searchQ.trim() && searchResults.length === 0 ? (
                <p className="muted">Ничего не найдено</p>
              ) : null}
              {searchResults.length > 0 ? (
                <ul className="adm-search-list">
                  {searchResults.map((r) => (
                    <li key={`${r.externalId}-${r.symbol}`}>
                      <button
                        type="button"
                        className="adm-search-item"
                        disabled={pending}
                        onClick={() => void onImportRemote(r)}
                      >
                        <strong>{r.symbol}</strong>
                        <span>{r.name}</span>
                        <span className="muted">{r.externalId}</span>
                      </button>
                    </li>
                  ))}
                </ul>
              ) : null}
              <p className="muted">Или заполните поля вручную на вкладке «Основное».</p>
            </div>
          ) : null}

          <div
            className={`adm-panel stack${tab === 'main' || kind !== 'stock' ? '' : ' adm-panel--hidden'}`}
            role="tabpanel"
          >
            <p className="muted" style={{ marginTop: 0 }}>
              {hint}
            </p>
            {kind === 'stock' && editing ? (
              <div className="row" style={{ gap: '0.5rem', marginBottom: '0.35rem' }}>
                <button type="button" className="btn btn-ghost" disabled={pending} onClick={() => void onRefreshMoex()}>
                  {pending ? 'Обновляем…' : 'Обновить с T‑Invest'}
                </button>
              </div>
            ) : null}
            <div className="field">
              <label htmlFor="adm-symbol">Тикер</label>
              <input
                id="adm-symbol"
                required
                value={draft.symbol}
                onChange={(e) => setDraft({ ...draft, symbol: e.target.value })}
              />
            </div>
            <div className="field">
              <label htmlFor="adm-ext">externalId</label>
              <input
                id="adm-ext"
                required
                value={draft.externalId}
                onChange={(e) => setDraft({ ...draft, externalId: e.target.value })}
              />
            </div>
            <div className="field">
              <label htmlFor="adm-name">Название</label>
              <input
                id="adm-name"
                required
                value={draft.name}
                onChange={(e) => setDraft({ ...draft, name: e.target.value })}
              />
            </div>
            <div className="field">
              <label htmlFor="adm-cur">Валюта</label>
              <select
                id="adm-cur"
                required
                value={draft.currency}
                onChange={(e) => setDraft({ ...draft, currency: e.target.value })}
              >
                <option value="RUB">RUB</option>
                <option value="USD">USD</option>
              </select>
            </div>
            {kind === 'stock' ? (
              <div className="field">
                <label htmlFor="adm-kind">Тип</label>
                <select
                  id="adm-kind"
                  value={draft.assetKind}
                  onChange={(e) => setDraft({ ...draft, assetKind: e.target.value as 'EQUITY' | 'BOND' })}
                >
                  <option value="EQUITY">Акция</option>
                  <option value="BOND">Облигация</option>
                </select>
              </div>
            ) : null}
            <label className="row" style={{ gap: '0.5rem' }}>
              <input
                type="checkbox"
                checked={draft.enabled}
                onChange={(e) => setDraft({ ...draft, enabled: e.target.checked })}
              />
              Показывать пользователям
            </label>
            <div className="field">
              <label htmlFor="adm-logo">Лого (png/jpg/webp/svg)</label>
              <input
                id="adm-logo"
                type="file"
                accept="image/png,image/jpeg,image/webp,image/svg+xml"
                onChange={(e) => setLogoFile(e.target.files?.[0] ?? null)}
              />
            </div>
          </div>

          {kind === 'stock' && editing ? (
            <div className={`adm-panel stack${tab === 'payments' ? '' : ' adm-panel--hidden'}`} role="tabpanel">
              <p className="muted" style={{ marginTop: 0 }}>
                История выплат с T‑Invest. По ней считается пассивный доход: прошлые годы — факт, текущий и будущие —
                прогноз (медианный рост). При обновлении таблица перезаписывается.
              </p>
              <label className="row" style={{ gap: '0.5rem' }}>
                <input
                  type="checkbox"
                  checked={draft.paysDividends}
                  onChange={(e) => setDraft({ ...draft, paysDividends: e.target.checked })}
                />
                Платит дивиденды (если снято — в прогноз текущего и будущих лет не входит)
              </label>
              {paymentsLoading ? (
                <p className="muted">Загрузка…</p>
              ) : payments.length === 0 ? (
                <p className="muted">Выплат пока нет — нажмите «Обновить с T‑Invest».</p>
              ) : (
                <div className="adm-pay-wrap">
                  <table className="adm-pay-table">
                    <thead>
                      <tr>
                        <th>Дата</th>
                        <th>Тип</th>
                        <th>На 1 шт</th>
                      </tr>
                    </thead>
                    <tbody>
                      {payments.map((p) => (
                        <tr key={p.id}>
                          <td>{p.occurredOn}</td>
                          <td>{paymentKindLabel(p.kind)}</td>
                          <td>{formatMoney(p.amountPerUnit, p.currency)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          ) : null}

          {error && modalOpen && <p className="error">{error}</p>}
          <div className="modal-actions">
            <button type="button" className="btn btn-ghost" disabled={pending} onClick={closeModal}>
              Отмена
            </button>
            {tab !== 'import' ? (
              <button type="submit" className="btn" disabled={pending}>
                {pending ? 'Сохраняем…' : 'Сохранить'}
              </button>
            ) : null}
          </div>
        </form>
      </Modal>
    </div>
  )
}
