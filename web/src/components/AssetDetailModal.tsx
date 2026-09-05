import { useEffect, useMemo, useState } from 'react'
import {
  Area,
  CartesianGrid,
  ComposedChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { ApiError } from '../api/client'
import * as portfoliosApi from '../api/portfolios'
import type { HoldingDetail, PortfolioKind } from '../api/types'
import { changeClass, formatMoney, formatPct } from '../lib/format'
import { AssetLogo } from './AssetLogo'
import { Modal } from './Modal'
import './AssetDetailModal.css'

type Props = {
  open: boolean
  kind: PortfolioKind
  portfolioId: string
  holdingId: string | null
  onClose: () => void
}

function formatAxisDate(iso: string) {
  const d = new Date(iso + 'T00:00:00')
  return d.toLocaleDateString('ru-RU', { month: 'short', year: '2-digit' })
}

function downsample<T>(rows: T[], maxPoints: number): T[] {
  if (rows.length <= maxPoints) return rows
  const step = Math.ceil(rows.length / maxPoints)
  const out: T[] = []
  for (let i = 0; i < rows.length; i += step) out.push(rows[i])
  const last = rows[rows.length - 1]
  if (out[out.length - 1] !== last) out.push(last)
  return out
}

function basisLabel(basis: string | null | undefined) {
  if (basis === 'ACTUAL') return 'факт'
  if (basis === 'FORECAST') return 'прогноз'
  return null
}

export function AssetDetailModal({ open, kind, portfolioId, holdingId, onClose }: Props) {
  const [detail, setDetail] = useState<HoldingDetail | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!open || !portfolioId || !holdingId) return
    let cancelled = false
    setLoading(true)
    setError(null)
    setDetail(null)
    void portfoliosApi
      .getHoldingDetail(kind, portfolioId, holdingId)
      .then((data) => {
        if (!cancelled) setDetail(data)
      })
      .catch((err) => {
        if (!cancelled) {
          setError(err instanceof ApiError ? err.message : 'Не удалось загрузить карточку')
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [open, kind, portfolioId, holdingId])

  const currency = detail?.currency || (kind === 'crypto' ? 'USD' : 'RUB')
  const year = new Date().getFullYear()

  const chartData = useMemo(() => {
    const rows = (detail?.priceHistory ?? []).map((p) => ({
      date: p.date,
      label: formatAxisDate(p.date),
      value: Number(p.value),
    }))
    return downsample(rows, 280)
  }, [detail])

  const title = detail ? `${detail.name || detail.symbol}` : 'Актив'

  return (
    <Modal open={open} title={title} onClose={onClose} wide>
      {loading ? <p className="muted">Загрузка…</p> : null}
      {error ? <p className="error">{error}</p> : null}
      {detail && !loading ? (
        <div className="asset-detail">
          <div className="asset-detail-head">
            <AssetLogo symbol={detail.symbol} name={detail.name} logoUrl={detail.logoUrl} size={44} />
            <div>
              <div className="asset-detail-symbol">{detail.symbol}</div>
              {!detail.cash ? (
                <div className="muted">
                  {Number(detail.quantity)} шт · позиция {formatMoney(detail.marketValue, currency)}
                </div>
              ) : (
                <div className="muted">Наличные</div>
              )}
            </div>
          </div>

          <div className="asset-detail-metrics">
            <div>
              <div className="asset-detail-label">Цена</div>
              <strong>{formatMoney(detail.unitPrice, currency)}</strong>
            </div>
            <div>
              <div className="asset-detail-label">За сегодня</div>
              <strong className={changeClass(detail.dayChangeAbs)}>
                {formatMoney(detail.dayChangeAbs, currency)}
                <span className={`asset-detail-pct ${changeClass(detail.dayChangePct)}`}>
                  {formatPct(detail.dayChangePct)}
                </span>
              </strong>
            </div>
          </div>

          {!detail.cash ? (
            <section className="asset-detail-chart-block">
              <h3>Цена · 5 лет</h3>
              {chartData.length === 0 ? (
                <p className="muted">Нет истории котировок</p>
              ) : (
                <div className="asset-detail-chart">
                  <ResponsiveContainer width="100%" height={280}>
                    <ComposedChart data={chartData} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
                      <defs>
                        <linearGradient id="assetPriceFill" x1="0" y1="0" x2="0" y2="1">
                          <stop offset="0%" stopColor="var(--accent)" stopOpacity={0.28} />
                          <stop offset="100%" stopColor="var(--accent)" stopOpacity={0} />
                        </linearGradient>
                      </defs>
                      <CartesianGrid stroke="var(--line)" strokeDasharray="3 6" vertical={false} />
                      <XAxis
                        dataKey="label"
                        tick={{ fill: 'var(--muted)', fontSize: 11 }}
                        axisLine={false}
                        tickLine={false}
                        minTickGap={40}
                      />
                      <YAxis
                        domain={['auto', 'auto']}
                        tick={{ fill: 'var(--muted)', fontSize: 11 }}
                        axisLine={false}
                        tickLine={false}
                        width={64}
                        tickFormatter={(v) =>
                          new Intl.NumberFormat('ru-RU', { maximumFractionDigits: 2 }).format(Number(v))
                        }
                      />
                      <Tooltip
                        contentStyle={{
                          background: 'var(--bg-elevated)',
                          border: '1px solid var(--line)',
                          borderRadius: 8,
                        }}
                        labelFormatter={(_, payload) => payload?.[0]?.payload?.date ?? ''}
                        formatter={(value) => [formatMoney(Number(value), currency), 'Цена']}
                      />
                      <Area
                        type="monotone"
                        dataKey="value"
                        stroke="var(--accent)"
                        fill="url(#assetPriceFill)"
                        strokeWidth={1.8}
                        dot={false}
                        isAnimationActive={false}
                      />
                    </ComposedChart>
                  </ResponsiveContainer>
                </div>
              )}
            </section>
          ) : null}

          {kind === 'stock' && !detail.cash ? (
            <section className="asset-detail-divs">
              <h3>Выплата на 1 шт</h3>
              <div className="asset-detail-metrics">
                <div>
                  <div className="asset-detail-label">
                    {year}
                    {basisLabel(detail.dividendThisYearBasis)
                      ? ` · ${basisLabel(detail.dividendThisYearBasis)}`
                      : ''}
                  </div>
                  <strong>
                    {detail.dividendThisYearPerUnit != null
                      ? formatMoney(detail.dividendThisYearPerUnit, currency)
                      : '—'}
                  </strong>
                </div>
                <div>
                  <div className="asset-detail-label">
                    {year + 1}
                    {basisLabel(detail.dividendNextYearBasis)
                      ? ` · ${basisLabel(detail.dividendNextYearBasis)}`
                      : ''}
                  </div>
                  <strong>
                    {detail.dividendNextYearPerUnit != null
                      ? formatMoney(detail.dividendNextYearPerUnit, currency)
                      : '—'}
                  </strong>
                </div>
              </div>
            </section>
          ) : null}
        </div>
      ) : null}
    </Modal>
  )
}
