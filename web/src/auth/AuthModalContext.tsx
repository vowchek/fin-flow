import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useState,
  type ReactNode,
} from 'react'

type AuthModalState = {
  open: boolean
  mode: 'login' | 'register'
  openLogin: () => void
  openRegister: () => void
  close: () => void
}

const AuthModalContext = createContext<AuthModalState | null>(null)

export function AuthModalProvider({ children }: { children: ReactNode }) {
  const [open, setOpen] = useState(false)
  const [mode, setMode] = useState<'login' | 'register'>('login')

  const openLogin = useCallback(() => {
    setMode('login')
    setOpen(true)
  }, [])

  const openRegister = useCallback(() => {
    setMode('register')
    setOpen(true)
  }, [])

  const close = useCallback(() => setOpen(false), [])

  const value = useMemo(
    () => ({ open, mode, openLogin, openRegister, close }),
    [open, mode, openLogin, openRegister, close],
  )

  return <AuthModalContext.Provider value={value}>{children}</AuthModalContext.Provider>
}

export function useAuthModal() {
  const ctx = useContext(AuthModalContext)
  if (!ctx) throw new Error('useAuthModal must be used within AuthModalProvider')
  return ctx
}
