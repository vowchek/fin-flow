import { useEffect, useMemo, useState } from 'react'
import {
  Area,
  CartesianGrid,
  ComposedChart,
  Line,
  ResponsiveContainer,
  Scatter,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { ApiError } from '../api/client'
import * as portfoliosApi from '../api/portfolios'
import type { PortfolioKind, ValuePoint } from '../api/types'
import { formatMoney, formatPct } from '../lib/format'
import { Modal } from './Modal'
import './GrowthChartModal.css'

type Props = {
  open: boolean
  kind: PortfolioKind
  portfolioId: string
  portfolioName: string
  currency: string
  onClose: () => void
}

type ChartRow = {
  date: string
  label: string
  value: number
  invested: number | null
  profit: number | null
  profitPct: number | null
  peak: number | null
  peakKind: 'high' | 'low' | null
}

function formatAxisDate(iso: string) {
  const d = new Date(iso + 'T00:00:00')
  return d.toLocaleDateString('ru-RU', { day: '2-digit', month: 'short' })
}

function findPeaks(rows: Omit<ChartRow, 'peak' | 'peakKind'>[]): ChartRow[] {
  if (rows.length === 0) return []
  const marked = rows.map((row) => ({ ...row, peak: null as number | null, peakKind: null as 'high' | 'low' | null }))
  if (rows.length < 3) {
    const last = marked[marked.length - 1]
    last.peak = last.value
    last.peakKind = 'high'
    return marked
  }

  const candidates: { index: number; kind: 'high' | 'low'; score: number }[] = []
  for (let i = 1; i < rows.length - 1; i++) {
    const prev = rows[i - 1].value
    const cur = rows[i].value
    const next = rows[i + 1].value
    if (cur >= prev && cur >= next && (cur > prev || cur > next)) {
      candidates.push({ index: i, kind: 'high', score: cur })
    } else if (cur <= prev && cur <= next && (cur < prev || cur < next)) {
      candidates.push({ index: i, kind: 'low', score: -cur })
    }
  }

  candidates.sort((a, b) => b.score - a.score)
  const picked = new Set(candidates.slice(0, 8).map((c) => c.index))
  // Always keep global max/min if present.
  let maxIdx = 0
  let minIdx = 0
  for (let i = 1; i < rows.length; i++) {
    if (rows[i].value > rows[maxIdx].value) maxIdx = i
    if (rows[i].value < rows[minIdx].value) minIdx = i
  }
  picked.add(maxIdx)
  picked.add(minIdx)

  for (const idx of picked) {
    const kind = candidates.find((c) => c.index === idx)?.kind
      ?? (idx === maxIdx ? 'high' : 'low')
    marked[idx].peak = marked[idx].value
    marked[idx].peakKind = kind
  }
  return marked
}

export function GrowthChartModal({ open, kind, portfolioId, portfolioName, currency, onClose }: Props) {
  const [points, setPoints] = useState<ValuePoint[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!open || !portfolioId) return
    let cancelled = false
    setLoading(true)
    setError(null)
    setPoints([])
    void portfoliosApi
      .getValueHistory(kind, portfolioId)
      .then((data) => {
        if (!cancelled) setPoints(data)
      })
      .catch((err) => {
        if (!cancelled) {
          setError(err instanceof ApiError ? err.message : 'Не удалось загрузить график')
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [open, kind, portfolioId])

  const chartData = useMemo(() => {
    const rows = points.map((p) => {
      const value = Number(p.value)
      const invested = p.invested == null ? null : Number(p.invested)
      const profit = invested == null ? null : value - invested
      const profitPct = invested != null && invested !== 0 && profit != null ? (profit / invested) * 100 : null
      return {
        date: p.date,
        label: formatAxisDate(p.date),
        value,
        invested,
        profit,
        profitPct,
      }
    })
    return findPeaks(rows)
  }, [points])

  const last = chartData[chartData.length - 1]
  const profit = last?.profit ?? null
  const profitPct = last?.profitPct ?? null

  return (
    <Modal open={open} wide title={`Рост · ${portfolioName}`} onClose={onClose}>
      <div className="growth-modal">
        {loading ? <p className="muted">Считаем историю по дневным ценам…</p> : null}
        {error ? <p className="error">{error}</p> : null}
        {!loading && !error && chartData.length === 0 ? (
          <p className="muted">Пока нет данных для графика — добавьте сделки с датами.</p>
        ) : null}
        {!loading && chartData.length > 0 ? (
          <>
            <div className="growth-summary">
              <div>
                <span className="growth-summary-label">Стоимость</span>
                <strong>{formatMoney(last?.value, currency)}</strong>
              </div>
              <div>
                <span className="growth-summary-label">Вложено</span>
                <strong>{formatMoney(last?.invested, currency)}</strong>
              </div>
              <div>
                <span className="growth-summary-label">Прибыль</span>
                <strong className={profit == null || profit === 0 ? '' : profit > 0 ? 'chg up' : 'chg down'}>
                  {formatMoney(profit, currency)}
                  {profitPct != null ? ` (${formatPct(profitPct)})` : ''}
                </strong>
              </div>
            </div>
            <div className="growth-legend">
              <span className="growth-legend-item value">Стоимость</span>
              <span className="growth-legend-item invested">Вложено</span>
              <span className="growth-legend-item peak">Пики</span>
            </div>
            <div className="growth-chart">
              <ResponsiveContainer width="100%" height={300}>
                <ComposedChart data={chartData} margin={{ top: 12, right: 8, left: 0, bottom: 0 }}>
                  <defs>
                    <linearGradient id="growthFill" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="0%" stopColor="var(--accent)" stopOpacity={0.28} />
                      <stop offset="100%" stopColor="var(--accent)" stopOpacity={0.02} />
                    </linearGradient>
                  </defs>
                  <CartesianGrid stroke="var(--line)" vertical={false} />
                  <XAxis
                    dataKey="label"
                    tick={{ fill: 'var(--muted)', fontSize: 11 }}
                    axisLine={false}
                    tickLine={false}
                    minTickGap={28}
                  />
                  <YAxis
                    tick={{ fill: 'var(--muted)', fontSize: 11 }}
                    axisLine={false}
                    tickLine={false}
                    width={64}
                    tickFormatter={(v) =>
                      Number(v).toLocaleString('ru-RU', {
                        notation: 'compact',
                        maximumFractionDigits: 1,
                      })
                    }
                  />
                  <Tooltip
                    contentStyle={{
                      background: 'var(--bg-elevated)',
                      border: '1px solid var(--line)',
                      borderRadius: 10,
                      color: 'var(--ink)',
                    }}
                    labelFormatter={(_, payload) => {
                      const row = payload?.[0]?.payload as ChartRow | undefined
                      if (!row?.date) return ''
                      const date = new Date(row.date + 'T00:00:00').toLocaleDateString('ru-RU')
                      if (row.peakKind) {
                        return `${date} · ${row.peakKind === 'high' ? 'пик' : 'минимум'}`
                      }
                      return date
                    }}
                    formatter={(value, name, item) => {
                      const row = item?.payload as ChartRow | undefined
                      const key = String(name)
                      if (key === 'peak') {
                        return [
                          `${formatMoney(row?.profit, currency)}${
                            row?.profitPct != null ? ` (${formatPct(row.profitPct)})` : ''
                          }`,
                          'Прибыль',
                        ]
                      }
                      if (key === 'invested') return [formatMoney(Number(value), currency), 'Вложено']
                      if (key === 'value') return [formatMoney(Number(value), currency), 'Стоимость']
                      return [formatMoney(Number(value), currency), key]
                    }}
                  />
                  <Area
                    type="monotone"
                    dataKey="value"
                    name="value"
                    stroke="var(--accent)"
                    strokeWidth={2.2}
                    fill="url(#growthFill)"
                    dot={false}
                    activeDot={{ r: 4 }}
                  />
                  <Line
                    type="monotone"
                    dataKey="invested"
                    name="invested"
                    stroke="var(--muted)"
                    strokeWidth={1.6}
                    strokeDasharray="5 4"
                    dot={false}
                    connectNulls
                  />
                  <Scatter
                    dataKey="peak"
                    name="peak"
                    fill="var(--accent)"
                    shape={(props: { cx?: number; cy?: number; payload?: ChartRow }) => {
                      const { cx = 0, cy = 0, payload } = props
                      if (payload?.peak == null) return <g />
                      const fill = payload.peakKind === 'low' ? 'var(--loss)' : 'var(--gain)'
                      return <circle cx={cx} cy={cy} r={5} fill={fill} stroke="var(--bg-elevated)" strokeWidth={2} />
                    }}
                  />
                </ComposedChart>
              </ResponsiveContainer>
            </div>
          </>
        ) : null}
        <div className="modal-actions">
          <button type="button" className="btn btn-ghost" onClick={onClose}>
            Закрыть
          </button>
        </div>
      </div>
    </Modal>
  )
}
