import { Navigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

export function RequireAdmin({ children }: { children: React.ReactNode }) {
  const { user, loading } = useAuth()
  if (loading) return <div className="page"><div className="panel empty">Загрузка…</div></div>
  if (!user) return <Navigate to="/login" replace />
  if (user.role !== 'ADMIN') return <Navigate to="/" replace />
  return children
}

