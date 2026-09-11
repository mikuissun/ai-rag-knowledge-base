import { request } from './http'
import type { AuthResponse, UserProfile } from '../types/api'

export type { AuthResponse, UserProfile }

export function registerUser(body: { username: string; password: string; nickname?: string; email?: string }) {
  return request<UserProfile>({ method: 'POST', url: '/api/auth/register', data: body })
}

export function login(body: { username: string; password: string }) {
  return request<AuthResponse>({ method: 'POST', url: '/api/auth/login', data: body })
}
