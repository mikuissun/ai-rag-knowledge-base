import { request } from './http'
import type { KnowledgeBase, KnowledgeBaseForm } from '../types/api'

export type { KnowledgeBase }

export function listKnowledgeBases() {
  return request<KnowledgeBase[]>({ method: 'GET', url: '/api/knowledge-bases' })
}

export function getKnowledgeBase(id: number) {
  return request<KnowledgeBase>({ method: 'GET', url: `/api/knowledge-bases/${id}` })
}

export function createKnowledgeBase(body: KnowledgeBaseForm) {
  return request<KnowledgeBase>({ method: 'POST', url: '/api/knowledge-bases', data: body })
}

export function updateKnowledgeBase(id: number, body: KnowledgeBaseForm) {
  return request<KnowledgeBase>({ method: 'PUT', url: `/api/knowledge-bases/${id}`, data: body })
}

export function deleteKnowledgeBase(id: number) {
  return request<void>({ method: 'DELETE', url: `/api/knowledge-bases/${id}` })
}
