import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react'
import * as authApi from '../api/auth'
import type { User } from '../api/types'

type AuthState = {
  user: User | null
  token: string | null
  loading: boolean
  login: (email: string, password: string) => Promise<void>
  register: (email: string, password: string, displayName?: string) => Promise<void>
  logout: () => void
}

const AuthContext = createContext<AuthState | null>(null)

function persist(token: string | null, user: User | null) {
  if (token && user) {
    localStorage.setItem('ledger_token', token)
    localStorage.setItem('ledger_user', JSON.stringify(user))
  } else {
    localStorage.removeItem('ledger_token')
    localStorage.removeItem('ledger_user')
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(() => {
    const raw = localStorage.getItem('ledger_user')
    return raw ? (JSON.parse(raw) as User) : null
  })
  const [token, setToken] = useState<string | null>(() => localStorage.getItem('ledger_token'))
  const [loading, setLoading] = useState(Boolean(localStorage.getItem('ledger_token')))

  useEffect(() => {
    if (!token) {
      setLoading(false)
      return
    }
    let cancelled = false
    authApi
      .me()
      .then((me) => {
        if (!cancelled) {
          setUser(me)
          persist(token, me)
        }
      })
      .catch(() => {
        if (!cancelled) {
          setUser(null)
          setToken(null)
          persist(null, null)
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [token])

  const login = useCallback(async (email: string, password: string) => {
    const res = await authApi.login(email, password)
    setToken(res.accessToken)
    setUser(res.user)
    persist(res.accessToken, res.user)
  }, [])

  const register = useCallback(async (email: string, password: string, displayName?: string) => {
    const res = await authApi.register(email, password, displayName)
    setToken(res.accessToken)
    setUser(res.user)
    persist(res.accessToken, res.user)
  }, [])

  const logout = useCallback(() => {
    setToken(null)
    setUser(null)
    persist(null, null)
  }, [])

  const value = useMemo(
    () => ({ user, token, loading, login, register, logout }),
    [user, token, loading, login, register, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within AuthProvider')
  return ctx
}
