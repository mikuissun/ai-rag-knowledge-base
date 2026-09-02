export interface UserProfile {
  id: number
  username: string
  nickname: string | null
  email: string | null
}

export interface AuthResponse {
  token: string
  user: UserProfile
}

interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

async function request<T>(path: string, body: Record<string, string>): Promise<T> {
  const response = await fetch(`/api/auth/${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  const payload = (await response.json()) as ApiResponse<T>
  if (!response.ok || payload.code !== 200) {
    throw new Error(payload.message || '请求失败，请稍后重试')
  }
  return payload.data
}

export function registerUser(body: Record<string, string>) {
  return request<UserProfile>('register', body)
}

export function login(body: Record<string, string>) {
  return request<AuthResponse>('login', body)
}
