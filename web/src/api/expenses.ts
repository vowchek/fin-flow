import { api } from './client'
import type { Expense, ExpenseCategory, ExpenseSummary } from './types'

export function listCategories() {
  return api<ExpenseCategory[]>('/api/v1/expense-categories')
}

export function createCategory(name: string) {
  return api<ExpenseCategory>('/api/v1/expense-categories', {
    method: 'POST',
    body: JSON.stringify({ name }),
  })
}

export function updateCategory(id: string, name: string) {
  return api<ExpenseCategory>(`/api/v1/expense-categories/${id}`, {
    method: 'PUT',
    body: JSON.stringify({ name }),
  })
}

export function deleteCategory(id: string) {
  return api<void>(`/api/v1/expense-categories/${id}`, { method: 'DELETE' })
}

export function listExpenses(from: string, to: string) {
  const params = new URLSearchParams({ from, to })
  return api<Expense[]>(`/api/v1/expenses?${params}`)
}

export function getSummary(from: string, to: string) {
  const params = new URLSearchParams({ from, to })
  return api<ExpenseSummary>(`/api/v1/expenses/summary?${params}`)
}

export function createExpense(payload: {
  categoryId: string
  amount: number
  currency?: string
  yearMonth: string
  note?: string
}) {
  return api<Expense>('/api/v1/expenses', {
    method: 'POST',
    body: JSON.stringify({
      categoryId: payload.categoryId,
      amount: payload.amount,
      currency: payload.currency || null,
      yearMonth: payload.yearMonth,
      note: payload.note || null,
    }),
  })
}

export function updateExpense(
  id: string,
  payload: {
    categoryId: string
    amount: number
    currency?: string
    yearMonth: string
    note?: string
  }
) {
  return api<Expense>(`/api/v1/expenses/${id}`, {
    method: 'PUT',
    body: JSON.stringify({
      categoryId: payload.categoryId,
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
