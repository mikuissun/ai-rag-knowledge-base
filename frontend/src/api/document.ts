import { request } from './http'
import type { DocumentDetail, DocumentIndexResponse, DocumentListItem, DocumentProcessResponse } from '../types/api'

function documentsPath(knowledgeBaseId: number) {
  return `/api/knowledge-bases/${knowledgeBaseId}/documents`
}

export function listDocuments(knowledgeBaseId: number) {
  return request<DocumentListItem[]>({ method: 'GET', url: documentsPath(knowledgeBaseId) })
}

export function getDocument(knowledgeBaseId: number, documentId: number) {
  return request<DocumentDetail>({ method: 'GET', url: `${documentsPath(knowledgeBaseId)}/${documentId}` })
}

export function uploadDocument(knowledgeBaseId: number, file: File, onUploadProgress?: (percent: number) => void) {
  const data = new FormData()
  data.append('file', file)
  return request<DocumentDetail>({
    method: 'POST',
    url: documentsPath(knowledgeBaseId),
    data,
    onUploadProgress: (event) => {
      if (event.total) onUploadProgress?.(Math.round((event.loaded / event.total) * 100))
    },
    timeout: 120000,
  })
}

export function deleteDocument(knowledgeBaseId: number, documentId: number) {
  return request<void>({ method: 'DELETE', url: `${documentsPath(knowledgeBaseId)}/${documentId}` })
}

export function processDocument(knowledgeBaseId: number, documentId: number) {
  return request<DocumentProcessResponse>({
    method: 'POST',
    url: `${documentsPath(knowledgeBaseId)}/${documentId}/process`,
    timeout: 180000,
  })
}

export function indexDocument(knowledgeBaseId: number, documentId: number) {
  return request<DocumentIndexResponse>({
    method: 'POST',
    url: `${documentsPath(knowledgeBaseId)}/${documentId}/index`,
    timeout: 120000,
  })
}
