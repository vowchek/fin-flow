import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { ApiError } from '../api/client'
import * as expensesApi from '../api/expenses'
import type { ExpenseCategory } from '../api/types'
import { Modal } from './Modal'

type Props = {
  open: boolean
  onClose: () => void
  onChanged: () => void
}

export function CategoryModal({ open, onClose, onChanged }: Props) {
  const [categories, setCategories] = useState<ExpenseCategory[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  // add form
  const [newName, setNewName] = useState('')
  const [adding, setAdding] = useState(false)

  // edit state
  const [editId, setEditId] = useState<string | null>(null)
  const [editName, setEditName] = useState('')

  const reload = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      setCategories(await expensesApi.listCategories())
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Ошибка загрузки')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    if (open) {
      void reload()
      setNewName('')
      setEditId(null)
    }
  }, [open, reload])

  async function onAdd(e: FormEvent) {
    e.preventDefault()
    const name = newName.trim()
    if (!name) return
    setAdding(true)
    setError(null)
    try {
      await expensesApi.createCategory(name)
      setNewName('')
      await reload()
      onChanged()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Не удалось создать')
    } finally {
      setAdding(false)
    }
  }

  function startEdit(cat: ExpenseCategory) {
    setEditId(cat.id)
    setEditName(cat.name)
  }

  function cancelEdit() {
    setEditId(null)
  }

  async function saveEdit(id: string) {
    const name = editName.trim()
    if (!name) return
    setError(null)
    try {
      await expensesApi.updateCategory(id, name)
      setEditId(null)
      await reload()
      onChanged()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Не удалось сохранить')
    }
  }

  async function onDelete(id: string, name: string) {
    if (!confirm(`Удалить категорию «${name}»?`)) return
    setError(null)
    try {
      await expensesApi.deleteCategory(id)
      await reload()
      onChanged()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Не удалось удалить')
    }
  }

  return (
    <Modal open={open} title="Категории трат" onClose={onClose}>
      <form className="cat-add-form" onSubmit={onAdd}>
        <input
          value={newName}
          onChange={(e) => setNewName(e.target.value)}
          placeholder="Название новой категории"
          className="cat-add-input"
          autoFocus
        />
        <button className="btn btn-sm" type="submit" disabled={adding || !newName.trim()}>
          {adding ? '…' : 'Добавить'}
        </button>
      </form>

      {error && <p className="error" style={{ margin: '0.5rem 0' }}>{error}</p>}

      {loading ? (
        <p className="muted">Загрузка…</p>
      ) : categories.length === 0 ? (
        <div className="empty" style={{ padding: '1.5rem 1rem' }}>
          Нет категорий. Добавьте первую выше.
        </div>
      ) : (
        <ul className="cat-list">
          {categories.map((cat) =>
            editId === cat.id ? (
              <li key={cat.id} className="cat-row editing">
                <input
                  value={editName}
                  onChange={(e) => setEditName(e.target.value)}
                  className="cat-edit-input"
                  autoFocus
                  onKeyDown={(e) => {
                    if (e.key === 'Enter') void saveEdit(cat.id)
                    if (e.key === 'Escape') cancelEdit()
                  }}
                />
                <div className="cat-row-btns">
                  <button type="button" className="btn btn-sm" onClick={() => void saveEdit(cat.id)}>
                    OK
                  </button>
                  <button type="button" className="btn btn-ghost btn-sm" onClick={cancelEdit}>
                    Отмена
                  </button>
                </div>
              </li>
            ) : (
              <li key={cat.id} className="cat-row">
                <span className="cat-name">{cat.name}</span>
                <div className="cat-row-btns">
                  <button type="button" className="icon-btn" title="Редактировать" onClick={() => startEdit(cat)}>
                    <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                      <path d="M17 3a2.83 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z" />
                      <path d="m15 5 4 4" />
                    </svg>
                  </button>
                  <button type="button" className="icon-btn danger" title="Удалить" onClick={() => void onDelete(cat.id, cat.name)}>
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
      )}
    </Modal>
  )
}
