import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react'
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  PieChart,
  Pie,
  Cell,
  Legend,
} from 'recharts'
import { ApiError } from '../../api/client'
import * as expensesApi from '../../api/expenses'
import type { Expense, ExpenseCategory, ExpenseSummary } from '../../api/types'
import { formatMoney } from '../../lib/format'
import { CategoryModal } from '../../components/CategoryModal'

const MONTHS_SHORT = ['Янв', 'Фев', 'Мар', 'Апр', 'Май', 'Июн', 'Июл', 'Авг', 'Сен', 'Окт', 'Ноя', 'Дек']
const MONTHS_FULL = [
  'Январь', 'Февраль', 'Март', 'Апрель', 'Май', 'Июнь',
  'Июль', 'Август', 'Сентябрь', 'Октябрь', 'Ноябрь', 'Декабрь',
]

const CATEGORY_COLORS = [
  '#1f8a84', '#c24b4b', '#d4a843', '#4a8ec2', '#8b5cb8', '#c47a3e',
  '#3ea87a', '#c26b8a', '#6b8fc2', '#8ac26b', '#c2a86b', '#7a6bc2',
]

type Tab = 'operations' | 'analytics'

function ymStr(year: number, month: number) {
  return `${year}-${String(month + 1).padStart(2, '0')}`
}

export function ExpensesPage() {
  const now = new Date()
  const [year, setYear] = useState(now.getFullYear())
  const [selectedMonth, setSelectedMonth] = useState(now.getMonth())

  const currentYM = ymStr(year, selectedMonth)

  const [categories, setCategories] = useState<ExpenseCategory[]>([])
  const [items, setItems] = useState<Expense[]>([])
  const [summary, setSummary] = useState<ExpenseSummary | null>(null)
  const [tab, setTab] = useState<Tab>('operations')
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  // quick-add
  const [qCategoryId, setQCategoryId] = useState('')
  const [qAmount, setQAmount] = useState('')
  const [qNote, setQNote] = useState('')
  const [qSaving, setQSaving] = useState(false)

  // edit
  const [editId, setEditId] = useState<string | null>(null)
  const [editCategoryId, setEditCategoryId] = useState('')
  const [editAmount, setEditAmount] = useState('')
  const [editNote, setEditNote] = useState('')

  // category modal
  const [catModalOpen, setCatModalOpen] = useState(false)

  const catById = useMemo(
    () => Object.fromEntries(categories.map((c) => [c.id, c])),
    [categories],
  )

  const yearFrom = ymStr(year, 0)
  const yearTo = ymStr(year, 11)

  const reload = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const [cats, list, sum] = await Promise.all([
        expensesApi.listCategories(),
        expensesApi.listExpenses(currentYM, currentYM),
        expensesApi.getSummary(yearFrom, yearTo),
      ])
      setCategories(cats)
      if (!qCategoryId && cats.length) setQCategoryId(cats[0].id)
      setItems(list)
      setSummary(sum)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Ошибка загрузки')
    } finally {
      setLoading(false)
    }
  }, [currentYM, yearFrom, yearTo, qCategoryId])

  useEffect(() => {
    void reload()
  }, [reload])

  const monthTotals = useMemo(() => {
    if (!summary) return new Map<string, number>()
    const totals = new Map<string, number>()
    for (const byMonth of Object.values(summary.byCategoryByMonth)) {
      for (const [m, amount] of Object.entries(byMonth)) {
        totals.set(m, (totals.get(m) || 0) + Number(amount))
      }
    }
    return totals
  }, [summary])

  async function onQuickAdd(e: FormEvent) {
    e.preventDefault()
    if (!qCategoryId) {
      setError('Сначала создайте категорию')
      return
    }
    setQSaving(true)
    setError(null)
    try {
      await expensesApi.createExpense({
        categoryId: qCategoryId,
        amount: Number(qAmount),
        yearMonth: currentYM,
        note: qNote || undefined,
      })
      setQAmount('')
      setQNote('')
      await reload()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Не удалось сохранить')
    } finally {
      setQSaving(false)
    }
  }

  function startEdit(item: Expense) {
    setEditId(item.id)
    setEditCategoryId(item.categoryId)
    setEditAmount(String(item.amount))
    setEditNote(item.note || '')
  }

  function cancelEdit() {
    setEditId(null)
  }

  async function saveEdit(id: string) {
    setError(null)
    try {
      await expensesApi.updateExpense(id, {
        categoryId: editCategoryId,
        amount: Number(editAmount),
        yearMonth: currentYM,
        note: editNote || undefined,
      })
      setEditId(null)
      await reload()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Не удалось сохранить')
    }
  }

  async function onDelete(id: string) {
    setError(null)
    try {
      await expensesApi.deleteExpense(id)
      await reload()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Не удалось удалить')
    }
  }

  // analytics
  const chartData = useMemo(() => {
    if (!summary) return []
    const months = new Set<string>()
    for (const cat of Object.values(summary.byCategoryByMonth)) {
      for (const m of Object.keys(cat)) months.add(m)
    }
    const sorted = Array.from(months).sort()
    return sorted.map((month) => {
      const row: Record<string, string | number> = { month }
      for (const [catId, byMonth] of Object.entries(summary.byCategoryByMonth)) {
        row[catId] = byMonth[month] || 0
      }
      return row
    })
  }, [summary])

  const pieData = useMemo(() => {
    if (!summary) return []
    return Object.entries(summary.totalsByCategory)
      .filter(([, v]) => v > 0)
      .sort((a, b) => b[1] - a[1])
      .map(([catId, value]) => ({
        id: catId,
        name: catById[catId]?.name || '…',
        value,
      }))
  }, [summary, catById])

  const activeCategories = useMemo(() => {
    if (!summary) return []
    return Object.keys(summary.totalsByCategory).filter(
      (catId) => (summary.totalsByCategory[catId] || 0) > 0,
    )
  }, [summary])

  const yearTotal = summary?.total || 0

  return (
    <div className="page">
      <div className="page-head">
        <div>
          <h1 className="page-title">Учёт трат</h1>
          <p className="page-lead">
            Фиксируйте расходы по категориям и отслеживайте динамику.
          </p>
        </div>
      </div>

      {/* Year navigator + month grid */}
      <div className="panel exp-year-panel">
        <div className="exp-year-header">
          <button type="button" className="icon-btn" onClick={() => setYear((y) => y - 1)}>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
              <path d="M15 18l-6-6 6-6" />
            </svg>
          </button>
          <span className="exp-year-label">{year}</span>
          <button type="button" className="icon-btn" onClick={() => setYear((y) => y + 1)}>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
              <path d="M9 18l6-6-6-6" />
            </svg>
          </button>
          {year !== now.getFullYear() && (
            <button
              type="button"
              className="btn btn-ghost btn-sm"
              onClick={() => { setYear(now.getFullYear()); setSelectedMonth(now.getMonth()) }}
              style={{ marginLeft: '0.5rem', fontSize: '0.78rem' }}
            >
              Сегодня
            </button>
          )}
        </div>
        <div className="exp-month-grid">
          {MONTHS_SHORT.map((label, i) => {
            const key = ymStr(year, i)
            const total = monthTotals.get(key) || 0
            const isCurrent = i === now.getMonth() && year === now.getFullYear()
            return (
              <button
                key={i}
                type="button"
                className={`exp-month-btn${i === selectedMonth ? ' selected' : ''}${isCurrent ? ' current' : ''}`}
                onClick={() => setSelectedMonth(i)}
              >
                <span className="exp-month-btn-label">{label}</span>
                {total > 0 && (
                  <span className="exp-month-btn-total">
                    {total >= 1000 ? `${(total / 1000).toFixed(1)}k` : total.toLocaleString('ru-RU')}
                  </span>
                )}
              </button>
            )
          })}
        </div>
        <div className="exp-year-footer">
          <span className="exp-year-total-label">Итого за {year}:</span>
          <span className="exp-year-total-value">{formatMoney(yearTotal)}</span>
        </div>
      </div>

      {/* Quick-add form + Categories button */}
      <div className="panel exp-add-panel">
        <div className="exp-add-top">
          <button
            type="button"
            className="btn btn-ghost btn-sm"
            onClick={() => setCatModalOpen(true)}
          >
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" style={{ marginRight: '0.3rem', verticalAlign: '-2px' }}>
              <path d="M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z" />
              <circle cx="12" cy="12" r="3" />
            </svg>
            Категории
          </button>
          {categories.length === 0 && (
            <span className="exp-add-hint-inline">Сначала добавьте хотя бы одну категорию</span>
          )}
        </div>
        <form className="exp-add-row" onSubmit={onQuickAdd}>
          <div className="field exp-add-cat">
            <label htmlFor="q-cat">Категория</label>
            <select
              id="q-cat"
              value={qCategoryId}
              onChange={(e) => setQCategoryId(e.target.value)}
              required
              disabled={categories.length === 0}
            >
              {categories.length === 0 && <option>Нет категорий</option>}
              {categories.map((c) => (
                <option key={c.id} value={c.id}>{c.name}</option>
              ))}
            </select>
          </div>
          <div className="field exp-add-amount">
            <label htmlFor="q-amount">Сумма</label>
            <input
              id="q-amount"
              type="number"
              min="0"
              step="0.01"
              required
              value={qAmount}
              onChange={(e) => setQAmount(e.target.value)}
              placeholder="0"
            />
          </div>
          <div className="field exp-add-note">
            <label htmlFor="q-note">Заметка</label>
            <input
              id="q-note"
              value={qNote}
              onChange={(e) => setQNote(e.target.value)}
              placeholder="Необязательно"
            />
          </div>
          <button className="btn exp-add-btn" type="submit" disabled={qSaving || categories.length === 0}>
            {qSaving ? '…' : 'Добавить'}
          </button>
        </form>
        <div className="exp-add-hint">
          Запишется в <strong>{MONTHS_FULL[selectedMonth]} {year}</strong>
        </div>
      </div>

      <CategoryModal
        open={catModalOpen}
        onClose={() => setCatModalOpen(false)}
        onChanged={() => void reload()}
      />

      {error && <p className="error" style={{ marginBottom: '0.75rem' }}>{error}</p>}

      {/* Tabs */}
      <div className="exp-tabs">
        <button
          type="button"
          className={`exp-tab${tab === 'operations' ? ' active' : ''}`}
          onClick={() => setTab('operations')}
        >
          Записи за {MONTHS_FULL[selectedMonth]}
        </button>
        <button
          type="button"
          className={`exp-tab${tab === 'analytics' ? ' active' : ''}`}
          onClick={() => setTab('analytics')}
        >
          Аналитика за {year}
        </button>
      </div>

      {loading ? (
        <p className="muted">Загрузка…</p>
      ) : (
        <>
          {/* OPERATIONS TAB */}
          {tab === 'operations' && (
            <div className="panel exp-ops-panel">
              {items.length === 0 ? (
                <div className="empty">Нет трат за {MONTHS_FULL[selectedMonth]} {year}</div>
              ) : (
                <>
                  <div className="exp-ops-header">
                    <span className="exp-ops-count">
                      {items.length} {plural(items.length, 'запись', 'записи', 'записей')}
                    </span>
                    <span className="exp-ops-total">
                      {formatMoney(items.reduce((s, i) => s + i.amount, 0))}
                    </span>
                  </div>
                  <ul className="exp-list">
                    {items.map((item) =>
                      editId === item.id ? (
                        <li key={item.id} className="exp-row editing">
                          <div className="exp-edit-fields">
                            <select
                              value={editCategoryId}
                              onChange={(e) => setEditCategoryId(e.target.value)}
                              className="exp-edit-cat"
                            >
                              {categories.map((c) => (
                                <option key={c.id} value={c.id}>{c.name}</option>
                              ))}
                            </select>
                            <input
                              type="number"
                              min="0"
                              step="0.01"
                              value={editAmount}
                              onChange={(e) => setEditAmount(e.target.value)}
                              className="exp-edit-amt"
                            />
                            <input
                              value={editNote}
                              onChange={(e) => setEditNote(e.target.value)}
                              placeholder="Заметка"
                              className="exp-edit-note"
                            />
                          </div>
                          <div className="exp-row-btns">
                            <button type="button" className="btn btn-sm" onClick={() => void saveEdit(item.id)}>
                              OK
                            </button>
                            <button type="button" className="btn btn-ghost btn-sm" onClick={cancelEdit}>
                              Отмена
                            </button>
                          </div>
                        </li>
                      ) : (
                        <li key={item.id} className="exp-row">
                          <span className="exp-row-cat">
                            <span
                              className="exp-dot"
                              style={{
                                background: CATEGORY_COLORS[
                                  activeCategories.indexOf(item.categoryId) % CATEGORY_COLORS.length
                                ],
                              }}
                            />
                            {item.categoryName}
                          </span>
                          {item.note && <span className="exp-row-note">{item.note}</span>}
                          <span className="exp-row-amt">{formatMoney(item.amount)}</span>
                          <div className="exp-row-btns">
                            <button type="button" className="icon-btn" title="Редактировать" onClick={() => startEdit(item)}>
                              <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                                <path d="M17 3a2.83 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z" />
                                <path d="m15 5 4 4" />
                              </svg>
                            </button>
                            <button type="button" className="icon-btn danger" title="Удалить" onClick={() => void onDelete(item.id)}>
                              <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                                <path d="M3 6h18" />
                                <path d="M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6" />
                                <path d="M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2" />
                              </svg>
                            </button>
                          </div>
                        </li>
                      ),
                    )}
                  </ul>
                </>
              )}
            </div>
          )}

          {/* ANALYTICS TAB */}
          {tab === 'analytics' && (
            <div className="exp-analytics">
              {chartData.length === 0 ? (
                <div className="panel empty">Нет данных для аналитики за {year}</div>
              ) : (
                <>
                  <div className="panel">
                    <h3 className="panel-title">Динамика по месяцам</h3>
                    <div className="exp-chart-wrap">
                      <ResponsiveContainer>
                        <BarChart data={chartData} margin={{ top: 8, right: 12, bottom: 4, left: 8 }}>
                          <CartesianGrid strokeDasharray="3 3" stroke="var(--line)" />
                          <XAxis
                            dataKey="month"
                            tick={{ fill: 'var(--muted)', fontSize: 12 }}
                            tickFormatter={(v: string) => MONTHS_SHORT[Number(v.split('-')[1]) - 1]}
                          />
                          <YAxis
                            tick={{ fill: 'var(--muted)', fontSize: 12 }}
                            tickFormatter={(v: number) => v >= 1000 ? `${(v / 1000).toFixed(0)}k` : String(v)}
                          />
                          <Tooltip
                            contentStyle={{
                              background: 'var(--bg-elevated)',
                              border: '1px solid var(--line)',
                              borderRadius: 10,
                              color: 'var(--ink)',
                            }}
                            formatter={(value, name) => [
                              formatMoney(Number(value)),
                              catById[String(name)]?.name || '…',
                            ]}
                            labelFormatter={(l) => {
                              const [, m] = String(l).split('-')
                              return MONTHS_FULL[Number(m) - 1]
                            }}
                          />
                          <Legend
                            formatter={(value) => catById[String(value)]?.name || '…'}
                            wrapperStyle={{ fontSize: 12 }}
                          />
                          {activeCategories.map((catId, i) => (
                            <Bar
                              key={catId}
                              dataKey={catId}
                              stackId="a"
                              fill={CATEGORY_COLORS[i % CATEGORY_COLORS.length]}
                              radius={i === activeCategories.length - 1 ? [4, 4, 0, 0] : [0, 0, 0, 0]}
                            />
                          ))}
                        </BarChart>
                      </ResponsiveContainer>
                    </div>
                  </div>

                  <div className="exp-analytics-row">
                    <div className="panel exp-pie-panel">
                      <h3 className="panel-title">По категориям</h3>
                      <div className="exp-pie-wrap">
                        <ResponsiveContainer>
                          <PieChart>
                            <Pie
                              data={pieData}
                              dataKey="value"
                              nameKey="name"
                              innerRadius={50}
                              outerRadius={88}
                              paddingAngle={2}
                              stroke="none"
                            >
                              {pieData.map((_, i) => (
                                <Cell key={i} fill={CATEGORY_COLORS[i % CATEGORY_COLORS.length]} />
                              ))}
                            </Pie>
                            <Tooltip
                              contentStyle={{
                                background: 'var(--bg-elevated)',
                                border: '1px solid var(--line)',
                                borderRadius: 10,
                                color: 'var(--ink)',
                              }}
                              formatter={(value, name) => {
                                const pct = yearTotal > 0 ? ((Number(value) / yearTotal) * 100).toFixed(1) : '0'
                                return [`${formatMoney(Number(value))} (${pct}%)`, String(name)]
                              }}
                            />
                          </PieChart>
                        </ResponsiveContainer>
                      </div>
                    </div>
                    <div className="panel exp-totals-panel">
                      <h3 className="panel-title">Итого по категориям</h3>
                      <ul className="exp-totals-list">
                        {pieData.map((entry, i) => {
                          const pct = yearTotal > 0 ? ((entry.value / yearTotal) * 100).toFixed(1) : '0'
                          return (
                            <li key={entry.id} className="exp-totals-row">
                              <span className="exp-totals-left">
                                <span className="exp-dot" style={{ background: CATEGORY_COLORS[i % CATEGORY_COLORS.length] }} />
                                <span className="exp-totals-name">{entry.name}</span>
                              </span>
                              <span className="exp-totals-right">
                                <span className="exp-totals-pct">{pct}%</span>
                                <span className="exp-totals-val">{formatMoney(entry.value)}</span>
                              </span>
                            </li>
                          )
                        })}
                      </ul>
                    </div>
                  </div>
                </>
              )}
            </div>
          )}
        </>
      )}
    </div>
  )
}

function plural(n: number, one: string, few: string, many: string): string {
  const mod10 = n % 10
  const mod100 = n % 100
  if (mod100 >= 11 && mod100 <= 19) return many
  if (mod10 === 1) return one
  if (mod10 >= 2 && mod10 <= 4) return few
  return many
}
