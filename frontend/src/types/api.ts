export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

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

export interface KnowledgeBase {
  id: number
  name: string
  description: string | null
  status: number
  createdAt: string
  updatedAt: string
}

export interface KnowledgeBaseForm {
  name: string
  description: string
}

export type ProcessingStatus = 'PENDING' | 'PROCESSING' | 'PROCESSED' | 'FAILED' | string
export type IndexingStatus = 'PENDING' | 'INDEXING' | 'INDEXED' | 'FAILED' | string

export interface DocumentListItem {
  id: number
  knowledgeBaseId: number
  originalName: string
  fileType: string
  fileSize: number
  status: number
  createdAt: string
  updatedAt: string
  processingStatus: ProcessingStatus
  processingError: string | null
  processedAt: string | null
  indexingStatus: IndexingStatus
  indexingError: string | null
  indexedAt: string | null
}

export interface DocumentDetail extends DocumentListItem {
  contentText: string
}

export interface DocumentProcessResponse {
  documentId: number
  chunkCount: number
  embeddingCount: number
  embeddingModel: string
  processingStatus: string
}

export interface DocumentIndexResponse {
  documentId: number
  pointCount: number
  collection: string
  indexingStatus: string
}

export interface Citation {
  documentId: number
  documentName: string
  chunkId: number
  chunkIndex: number
  score: number
  contentSnippet: string
}

export interface ChatRequest {
  question: string
  topK?: number
}

export interface ChatResponse {
  answer: string
  sources: Citation[]
}
