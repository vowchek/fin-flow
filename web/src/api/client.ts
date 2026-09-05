const API_BASE = import.meta.env.VITE_API_URL ?? ''

export class ApiError extends Error {
  status: number

  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

function authHeader(): HeadersInit {
  const token = localStorage.getItem('ledger_token')
  return token ? { Authorization: `Bearer ${token}` } : {}
}

function parseBody(text: string): unknown {
  if (!text) return null
  try {
    return JSON.parse(text)
  } catch {
    return null
  }
}

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  if (!headers.has('Content-Type') && init.body) {
    headers.set('Content-Type', 'application/json')
  }

  const skipAuth = path.startsWith('/api/v1/auth/login') || path.startsWith('/api/v1/auth/register')
  if (!skipAuth) {
    const auth = authHeader()
    Object.entries(auth).forEach(([k, v]) => headers.set(k, String(v)))
  }

  let response: Response
  try {
    response = await fetch(`${API_BASE}${path}`, { ...init, headers })
  } catch {
    throw new ApiError(0, 'Сервер недоступен. Проверьте, что API запущен.')
  }

  if (response.status === 204) {
    return undefined as T
  }

  const text = await response.text()
  const data = parseBody(text) as { detail?: string; message?: string; title?: string } | null

  if (!response.ok) {
    const detail = data?.detail ?? data?.message ?? data?.title ?? (text.trim() || response.statusText)
    throw new ApiError(response.status, String(detail))
  }

  if (text && data == null) {
    throw new ApiError(response.status, 'Некорректный ответ сервера')
  }

  return data as T
}
