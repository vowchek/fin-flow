import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { ApiError } from '../api/client'
import { useAuth } from './AuthContext'
import './AuthModal.css'

type Mode = 'login' | 'register'

export function AuthModal({
  open,
  onClose,
  initialMode = 'login',
}: {
  open: boolean
  onClose: () => void
  initialMode?: Mode
}) {
  return (
    <AuthModalInner
      key={initialMode}
      open={open}
      onClose={onClose}
      initialMode={initialMode}
    />
  )
}

function AuthModalInner({
  open,
  onClose,
  initialMode,
}: {
  open: boolean
  onClose: () => void
  initialMode: Mode
}) {
  const { login, register } = useAuth()
  const navigate = useNavigate()
  const [mode, setMode] = useState<Mode>(initialMode)
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)
  const dialogRef = useRef<HTMLDialogElement>(null)

  useEffect(() => {
    const el = dialogRef.current
    if (!el) return
    if (open && !el.open) {
      el.showModal()
    } else if (!open && el.open) {
      el.close()
    }
  }, [open])

  const handleClose = useCallback(() => {
    setError(null)
    setEmail('')
    setPassword('')
    setDisplayName('')
    onClose()
  }, [onClose])

  const switchMode = useCallback(() => {
    setError(null)
    setMode((m) => (m === 'login' ? 'register' : 'login'))
  }, [])

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    setPending(true)
    setError(null)
    try {
      if (mode === 'login') {
        await login(email, password)
      } else {
        await register(email, password, displayName || undefined)
      }
      handleClose()
      navigate('/stocks')
    } catch (err) {
      setError(
        err instanceof ApiError
          ? err.message
          : mode === 'login'
            ? 'Не удалось войти'
            : 'Не удалось зарегистрироваться',
      )
    } finally {
      setPending(false)
    }
  }

  return (
    <dialog
      ref={dialogRef}
      className="auth-dialog"
      onClose={handleClose}
      onClick={(e) => {
        if (e.target === dialogRef.current) handleClose()
      }}
    >
      <div className="auth-card">
        <button
          type="button"
          className="auth-close"
          aria-label="Закрыть"
          onClick={handleClose}
        >
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden>
            <path d="M18 6L6 18M6 6l12 12" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
          </svg>
        </button>

        <div className="auth-header">
          <h2 className="auth-title">{mode === 'login' ? 'Вход' : 'Регистрация'}</h2>
          <p className="auth-subtitle">
            {mode === 'login'
              ? 'Войдите, чтобы открыть портфели и траты.'
              : 'Создайте аккаунт — данные будут только ваши.'}
          </p>
        </div>

        <form className="auth-form" onSubmit={onSubmit}>
          {mode === 'register' && (
            <div className="field">
              <label htmlFor="auth-name">Имя</label>
              <input
                id="auth-name"
                value={displayName}
                onChange={(e) => setDisplayName(e.target.value)}
                placeholder="Как к вам обращаться"
              />
            </div>
          )}
          <div className="field">
            <label htmlFor="auth-email">Email</label>
            <input
              id="auth-email"
              type="email"
              autoComplete="email"
              required
              value={email}
              onChange={(e) => setEmail(e.target.value)}
            />
          </div>
          <div className="field">
            <label htmlFor="auth-password">Пароль{mode === 'register' ? ' (мин. 8)' : ''}</label>
            <input
              id="auth-password"
              type="password"
              autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
              required
              minLength={8}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
          </div>
          {error && <p className="error">{error}</p>}
          <button className="btn auth-submit" type="submit" disabled={pending}>
            {pending
              ? mode === 'login'
                ? 'Входим…'
                : 'Создаём…'
              : mode === 'login'
                ? 'Войти'
                : 'Создать аккаунт'}
          </button>
        </form>

        <p className="auth-switch">
          {mode === 'login' ? (
            <>
              Нет аккаунта?{' '}
              <button type="button" className="auth-switch-btn" onClick={switchMode}>
                Регистрация
              </button>
            </>
          ) : (
            <>
              Уже есть аккаунт?{' '}
              <button type="button" className="auth-switch-btn" onClick={switchMode}>
                Войти
              </button>
            </>
          )}
        </p>
      </div>
    </dialog>
  )
}
