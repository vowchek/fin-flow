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

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  if (!headers.has('Content-Type') && init.body) {
    headers.set('Content-Type', 'application/json')
  }
  const auth = authHeader()
  Object.entries(auth).forEach(([k, v]) => headers.set(k, String(v)))

  const response = await fetch(`${API_BASE}${path}`, { ...init, headers })
  if (response.status === 204) {
    return undefined as T
  }

  const text = await response.text()
  const data = text ? JSON.parse(text) : null

  if (!response.ok) {
    const detail = data?.detail ?? data?.message ?? response.statusText
    throw new ApiError(response.status, String(detail))
  }
  return data as T
}
