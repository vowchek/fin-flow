import { Navigate } from 'react-router-dom'
import { useAuth } from './AuthContext'
import type { ReactNode } from 'react'

export function RequireAuth({ children }: { children: ReactNode }) {
  const { token, loading } = useAuth()

  if (loading) {
    return (
      <div className="page">
        <p className="muted">Загрузка…</p>
      </div>
    )
  }

  if (!token) {
    return <Navigate to="/" replace />
  }

  return children
}
