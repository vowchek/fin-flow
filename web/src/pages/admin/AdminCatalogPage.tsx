import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { ApiError } from '../../api/client'
import * as instrumentsApi from '../../api/instruments'
import type { Instrument, PortfolioKind } from '../../api/types'
import { AssetLogo } from '../../components/AssetLogo'
import { Modal } from '../../components/Modal'

type Draft = {
  symbol: string
  externalId: string
  name: string
  currency: string
  enabled: boolean
}

const emptyDraft = (kind: PortfolioKind): Draft => ({
  symbol: '',
  externalId: '',
  name: '',
  currency: kind === 'crypto' ? 'USD' : 'RUB',
  enabled: true,
})

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

  function openCreate() {
    setEditing(null)
    setDraft(emptyDraft(kind))
    setLogoFile(null)
    setCreating(true)
    setError(null)
  }

  function openEdit(item: Instrument) {
    setCreating(false)
    setEditing(item)
    setDraft({
      symbol: item.symbol,
      externalId: item.externalId,
      name: item.name,
      currency: item.currency,
      enabled: item.enabled,
    })
    setLogoFile(null)
    setError(null)
  }

  async function onSave(e: FormEvent) {
    e.preventDefault()
    setPending(true)
    setError(null)
    try {
      let saved: Instrument
      if (editing) {
        saved = await instrumentsApi.updateInstrument(kind, editing.id, draft)
      } else {
        saved = await instrumentsApi.createInstrument(kind, draft)
      }
      if (logoFile) {
        saved = await instrumentsApi.uploadLogo(kind, saved.id, logoFile)
      }
      setCreating(false)
      setEditing(null)
      setLogoFile(null)
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
        <button type="button" className="btn" onClick={openCreate}>
          Добавить
        </button>
      </div>

      <div className="row" style={{ gap: '0.5rem', marginBottom: '1rem' }}>
        <button type="button" className={`btn ${kind === 'stock' ? '' : 'btn-ghost'}`} onClick={() => setKind('stock')}>
          Фонд
        </button>
        <button type="button" className={`btn ${kind === 'crypto' ? '' : 'btn-ghost'}`} onClick={() => setKind('crypto')}>
          Крипта
        </button>
      </div>

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
                  </strong>
                  <span className="holding-meta">
                    {item.name} · {item.externalId}
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
        onClose={() => !pending && (setCreating(false), setEditing(null))}
      >
        <form className="stack" onSubmit={onSave}>
          <p className="muted" style={{ marginTop: 0 }}>
            {hint}
          </p>
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
          {error && modalOpen && <p className="error">{error}</p>}
          <div className="modal-actions">
            <button
              type="button"
              className="btn btn-ghost"
              disabled={pending}
              onClick={() => {
                setCreating(false)
                setEditing(null)
              }}
            >
              Отмена
            </button>
            <button type="submit" className="btn" disabled={pending}>
              {pending ? 'Сохраняем…' : 'Сохранить'}
            </button>
          </div>
        </form>
      </Modal>
    </div>
  )
}