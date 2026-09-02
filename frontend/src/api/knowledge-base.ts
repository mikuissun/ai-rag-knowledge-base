export interface KnowledgeBase {
  id: number
  name: string
  description: string | null
  status: number
  createdAt: string
  updatedAt: string
}

interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

async function request<T>(path: string, token: string, options: RequestInit = {}): Promise<T> {
  const response = await fetch(`/api/knowledge-bases${path}`, {
    ...options,
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
      ...options.headers,
    },
  })
  const payload = (await response.json()) as ApiResponse<T>
  if (!response.ok || payload.code !== 200) {
    throw new Error(payload.message || '请求失败，请稍后重试')
  }
  return payload.data
}

export function listKnowledgeBases(token: string) {
  return request<KnowledgeBase[]>('', token)
}

export function createKnowledgeBase(token: string, body: { name: string; description: string }) {
  return request<KnowledgeBase>('', token, { method: 'POST', body: JSON.stringify(body) })
}

export function updateKnowledgeBase(token: string, id: number, body: { name: string; description: string }) {
  return request<KnowledgeBase>(`/${id}`, token, { method: 'PUT', body: JSON.stringify(body) })
}

export function deleteKnowledgeBase(token: string, id: number) {
  return request<void>(`/${id}`, token, { method: 'DELETE' })
}
