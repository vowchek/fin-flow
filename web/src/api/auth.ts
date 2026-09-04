import { api } from './client'
import type { AuthResponse, User } from './types'

export function register(email: string, password: string, displayName?: string) {
  return api<AuthResponse>('/api/v1/auth/register', {
    method: 'POST',
    body: JSON.stringify({ email, password, displayName: displayName || null }),
  })
}

export function login(email: string, password: string) {
  return api<AuthResponse>('/api/v1/auth/login', {
    method: 'POST',
    body: JSON.stringify({ email, password }),
  })
}

export function me() {
  return api<User>('/api/v1/auth/me')
}
