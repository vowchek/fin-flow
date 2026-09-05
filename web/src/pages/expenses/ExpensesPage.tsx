import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { ApiError } from '../../api/client'
import * as expensesApi from '../../api/expenses'
import type { Expense, ExpenseCategory } from '../../api/types'

function currentYearMonth() {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
}

export function ExpensesPage() {
  const defaultMonth = useMemo(() => currentYearMonth(), [])
  const [from, setFrom] = useState(defaultMonth)
  const [to, setTo] = useState(defaultMonth)
  const [categories, setCategories] = useState<ExpenseCategory[]>([])
  const [items, setItems] = useState<Expense[]>([])
  const [category, setCategory] = useState('')
  const [amount, setAmount] = useState('')
  const [yearMonth, setYearMonth] = useState(defaultMonth)
  const [note, setNote] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  async function reload() {
    setLoading(true)
    setError(null)
    try {
      const [cats, list] = await Promise.all([
        expensesApi.listCategories(),
        expensesApi.listExpenses(from, to),
      ])
      setCategories(cats)
      if (!category && cats.length) setCategory(cats[0].code)
      setItems(list)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Ошибка загрузки')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void reload()
  }, [from, to])

  async function onCreate(e: FormEvent) {
    e.preventDefault()
    setError(null)
    try {
      await expensesApi.createExpense({
        category,
        amount: Number(amount),
        yearMonth,
        note: note || undefined,
      })
      setAmount('')
      setNote('')
      await reload()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Не удалось сохранить')
    }
  }

  async function onDelete(id: string) {
    try {
      await expensesApi.deleteExpense(id)
      await reload()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Не удалось удалить')
    }
  }

  const labelByCode = Object.fromEntries(categories.map((c) => [c.code, c.label]))

  return (
    <div className="page">
      <h1 className="page-title">Учёт трат</h1>
      <p className="page-lead">Записи по месяцам. Период задаётся в формате YYYY-MM.</p>

      <div className="panel row" style={{ marginBottom: '1rem' }}>
        <div className="field" style={{ maxWidth: 160 }}>
          <label htmlFor="from">С</label>
          <input id="from" pattern="\d{4}-\d{2}" value={from} onChange={(e) => setFrom(e.target.value)} />
        </div>
        <div className="field" style={{ maxWidth: 160 }}>
          <label htmlFor="to">По</label>
          <input id="to" pattern="\d{4}-\d{2}" value={to} onChange={(e) => setTo(e.target.value)} />
        </div>
      </div>

      <form className="panel row" onSubmit={onCreate} style={{ marginBottom: '1.25rem' }}>
        <div className="field">
          <label htmlFor="category">Категория</label>
          <select id="category" value={category} onChange={(e) => setCategory(e.target.value)} required>
            {categories.map((c) => (
              <option key={c.code} value={c.code}>
                {c.label}
              </option>
            ))}
          </select>
        </div>
        <div className="field" style={{ maxWidth: 140 }}>
          <label htmlFor="amount">Сумма</label>
          <input
            id="amount"
            type="number"
            min="0"
            step="0.01"
            required
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
          />
        </div>
        <div className="field" style={{ maxWidth: 140 }}>
          <label htmlFor="yearMonth">Месяц</label>
          <input
            id="yearMonth"
            pattern="\d{4}-\d{2}"
            required
            value={yearMonth}
            onChange={(e) => setYearMonth(e.target.value)}
          />
        </div>
        <div className="field">
          <label htmlFor="note">Заметка</label>
          <input id="note" value={note} onChange={(e) => setNote(e.target.value)} />
        </div>
        <button className="btn" type="submit" style={{ alignSelf: 'end' }}>
          Добавить
        </button>
      </form>

      {error && <p className="error">{error}</p>}
      {loading ? (
        <p className="muted">Загрузка…</p>
      ) : items.length === 0 ? (
        <div className="panel empty">Нет трат за выбранный период</div>
      ) : (
        <ul className="list">
          {items.map((item) => (
            <li key={item.id} className="list-item">
              <span>
                <strong>
                  {labelByCode[item.category] || item.category} · {item.yearMonth}
                </strong>
                <span className="muted">{item.note || 'Без заметки'}</span>
              </span>
              <span className="row">
                <strong>
                  {item.amount} {item.currency}
                </strong>
                <button type="button" className="btn btn-ghost" onClick={() => void onDelete(item.id)}>
                  Удалить
                </button>
              </span>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
