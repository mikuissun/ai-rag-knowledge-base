import { apiUrl, handleUnauthorized } from './http'
import { getAccessToken } from '../stores/auth'
import type { ChatRequest, ChatResponse, Citation } from '../types/api'

export interface StreamHandlers {
  onMessage: (delta: string) => void
  onSources: (sources: Citation[]) => void
  onDone: () => void
  onError: (message: string) => void
}

export async function chat(knowledgeBaseId: number, body: ChatRequest) {
  const response = await fetch(apiUrl(`/api/knowledge-bases/${knowledgeBaseId}/chat`), {
    method: 'POST',
    headers: { Authorization: `Bearer ${getAccessToken()}`, 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  const payload = await response.json().catch(() => null) as { code: number; message: string; data: ChatResponse } | null
  if (response.status === 401 || payload?.code === 401) handleUnauthorized()
  if (!response.ok || payload?.code !== 200 || !payload.data) throw new Error(payload?.message || '问答请求失败')
  return payload.data
}

function parseJson<T>(value: string, fallback: T) {
  try {
    return JSON.parse(value) as T
  } catch {
    return fallback
  }
}

function dispatchSseBlock(block: string, handlers: StreamHandlers) {
  let event = 'message'
  const data: string[] = []
  for (const line of block.split(/\r?\n/)) {
    if (line.startsWith('event:')) event = line.slice(6).trim()
    if (line.startsWith('data:')) data.push(line.slice(5).replace(/^ /, ''))
  }
  if (!data.length) return
  const raw = data.join('\n')
  if (event === 'message') {
    const payload = parseJson<{ delta?: string }>(raw, { delta: raw })
    if (payload.delta) handlers.onMessage(payload.delta)
  } else if (event === 'sources') {
    handlers.onSources(parseJson<Citation[]>(raw, []))
  } else if (event === 'done') {
    handlers.onDone()
  } else if (event === 'error') {
    const payload = parseJson<{ message?: string }>(raw, { message: raw })
    handlers.onError(payload.message || '流式问答失败')
  }
}

export async function streamChat(
  knowledgeBaseId: number,
  body: ChatRequest,
  handlers: StreamHandlers,
  signal: AbortSignal,
) {
  const response = await fetch(apiUrl(`/api/knowledge-bases/${knowledgeBaseId}/chat/stream`), {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${getAccessToken()}`,
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
    },
    body: JSON.stringify(body),
    signal,
  })
  if (response.status === 401) handleUnauthorized()
  if (!response.ok) {
    const payload = await response.json().catch(() => null) as { message?: string } | null
    throw new Error(payload?.message || `流式问答请求失败（${response.status}）`)
  }
  if (!response.body) throw new Error('浏览器不支持流式响应')

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  let done = false

  const consume = (text: string) => {
    buffer += text
    const blocks = buffer.split(/\r?\n\r?\n/)
    buffer = blocks.pop() ?? ''
    blocks.forEach((block) => dispatchSseBlock(block, handlers))
  }

  try {
    while (!done) {
      const result = await reader.read()
      done = result.done
      if (result.value) consume(decoder.decode(result.value, { stream: !done }))
    }
    const remaining = decoder.decode()
    if (remaining) consume(remaining)
    if (buffer.trim()) dispatchSseBlock(buffer, handlers)
  } finally {
    reader.releaseLock()
  }
}
