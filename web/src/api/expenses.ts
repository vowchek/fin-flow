import { api } from './client'
import type { Expense, ExpenseCategory } from './types'

export function listCategories() {
  return api<ExpenseCategory[]>('/api/v1/expense-categories')
}

export function listExpenses(from: string, to: string) {
  const params = new URLSearchParams({ from, to })
  return api<Expense[]>(`/api/v1/expenses?${params}`)
}

export function createExpense(payload: {
  category: string
  amount: number
  currency?: string
  yearMonth: string
  note?: string
}) {
  return api<Expense>('/api/v1/expenses', {
    method: 'POST',
    body: JSON.stringify({
      category: payload.category,
      amount: payload.amount,
      currency: payload.currency || null,
      yearMonth: payload.yearMonth,
      note: payload.note || null,
    }),
  })
}

export function deleteExpense(id: string) {
  return api<void>(`/api/v1/expenses/${id}`, { method: 'DELETE' })
}
