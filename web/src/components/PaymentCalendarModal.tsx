import { useEffect, useState } from 'react'
import { ApiError } from '../api/client'
import * as portfoliosApi from '../api/portfolios'
import type { PaymentCalendar } from '../api/types'
import { formatMoney } from '../lib/format'
import { Modal } from './Modal'

type Props = {
  open: boolean
  portfolioId: string
  portfolioName: string
  currency: string
  onClose: () => void
}

export function PaymentCalendarModal({ open, portfolioId, portfolioName, currency, onClose }: Props) {
  const [year, setYear] = useState(() => new Date().getFullYear())
  const [data, setData] = useState<PaymentCalendar | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!open) return
    setYear(new Date().getFullYear())
  }, [open, portfolioId])

  useEffect(() => {
    if (!open) return
    let cancelled = false
    setLoading(true)
    setError(null)
    void portfoliosApi
      .getPaymentCalendar(portfolioId, year)
      .then((res) => {
        if (!cancelled) setData(res)
      })
      .catch((err) => {
        if (!cancelled) {
          setData(null)
          setError(err instanceof ApiError ? err.message : 'Не удалось загрузить календарь')
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [open, portfolioId, year])

  return (
    <Modal open={open} title={`Календарь выплат · ${portfolioName}`} onClose={onClose}>
      <div className="pf-metric-head" style={{ marginBottom: '0.85rem' }}>
        <p className="muted" style={{ margin: 0 }}>
          Данные MOEX по позициям портфеля
        </p>
        <div className="pf-year-switch" aria-label="Год выплат">
          <button type="button" className="pf-year-btn" aria-label="Предыдущий год" onClick={() => setYear((y) => y - 1)}>
            ‹
          </button>
          <span className="pf-year-value">{year}</span>
          <button type="button" className="pf-year-btn" aria-label="Следующий год" onClick={() => setYear((y) => y + 1)}>
            ›
          </button>
        </div>
      </div>

      {loading ? <p className="muted">Загрузка…</p> : null}
      {error ? <p className="error">{error}</p> : null}

      {!loading && !error && data ? (
        data.items.length === 0 ? (
          <p className="muted">За {year} год выплат по позициям не найдено.</p>
        ) : (
          <>
            <p className="pay-cal-total">
              Итого: <strong>{formatMoney(data.totalAmount, data.currency || currency)}</strong>
            </p>
            <ul className="pay-cal-list">
              {data.items.map((item, idx) => (
                <li key={`${item.symbol}-${item.date}-${item.kind}-${idx}`} className="pay-cal-row">
                  <div>
                    <strong>{item.symbol}</strong>
                    <span className="muted"> · {item.kind === 'COUPON' ? 'Купон' : 'Дивиденд'}</span>
                    <div className="cell-sub">{item.date}</div>
                  </div>
                  <div className="pay-cal-amounts">
                    <strong>{formatMoney(item.amount, item.currency || currency)}</strong>
                    <span className="cell-sub">
                      {formatMoney(item.perUnit, item.currency || currency)} × {item.quantity}
                    </span>
                  </div>
                </li>
              ))}
            </ul>
          </>
        )
      ) : null}

      <div className="modal-actions">
        <button type="button" className="btn btn-ghost" onClick={onClose}>
          Закрыть
        </button>
      </div>
    </Modal>
  )
}
