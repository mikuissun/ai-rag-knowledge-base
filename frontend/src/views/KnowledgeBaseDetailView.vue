<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getKnowledgeBase } from '../api/knowledgeBase'
import { deleteDocument, getDocument, indexDocument, listDocuments, processDocument, uploadDocument } from '../api/document'
import ChatPanel from '../components/ChatPanel.vue'
import type { DocumentDetail, DocumentListItem, KnowledgeBase } from '../types/api'

const route = useRoute()
const router = useRouter()
const knowledgeBaseId = computed(() => Number(route.params.id))
const knowledgeBase = ref<KnowledgeBase | null>(null)
const documents = ref<DocumentListItem[]>([])
const loading = ref(true)
const error = ref('')
const selectedFile = ref<File | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)
const uploading = ref(false)
const uploadProgress = ref(0)
const busyAction = ref('')
const documentDialog = ref(false)
const documentLoading = ref(false)
const selectedDocument = ref<DocumentDetail | null>(null)

const acceptedExtensions = ['pdf', 'docx', 'md', 'markdown', 'txt']
const MAX_FILE_SIZE = 20 * 1024 * 1024

function formatSize(bytes: number) {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

function formatDate(value: string | null) {
  if (!value) return '-'
  return new Date(value).toLocaleString('zh-CN', { dateStyle: 'medium', timeStyle: 'short' })
}

function fileExtension(name: string) {
  return name.toLowerCase().split('.').pop() || ''
}

function processingType(status: string) {
  if (status === 'PROCESSED') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'PROCESSING') return 'warning'
  return 'info'
}

function indexingType(status: string) {
  if (status === 'INDEXED') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'INDEXING') return 'warning'
  return 'info'
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const id = knowledgeBaseId.value
    if (!Number.isSafeInteger(id) || id <= 0) throw new Error('知识库地址无效')
    const [base, items] = await Promise.all([getKnowledgeBase(id), listDocuments(id)])
    knowledgeBase.value = base
    documents.value = items
  } catch (requestError) {
    error.value = requestError instanceof Error ? requestError.message : '知识库加载失败'
  } finally {
    loading.value = false
  }
}

function chooseFile(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  const extension = fileExtension(file.name)
  if (!acceptedExtensions.includes(extension)) {
    ElMessage.error('仅支持 PDF、DOCX、Markdown 和 TXT 文件')
    input.value = ''
    return
  }
  if (file.size > MAX_FILE_SIZE) {
    ElMessage.error('文件大小不能超过 20 MiB')
    input.value = ''
    return
  }
  selectedFile.value = file
}

function clearFile() {
  selectedFile.value = null
  if (fileInput.value) fileInput.value.value = ''
}

async function upload() {
  if (!selectedFile.value) return
  uploading.value = true
  uploadProgress.value = 0
  try {
    await uploadDocument(knowledgeBaseId.value, selectedFile.value, (progress) => { uploadProgress.value = progress })
    ElMessage.success('文档上传并解析成功')
    clearFile()
    await load()
  } catch (requestError) {
    ElMessage.error(requestError instanceof Error ? requestError.message : '文档上传失败')
  } finally {
    uploading.value = false
  }
}

async function showDocument(item: DocumentListItem) {
  documentDialog.value = true
  documentLoading.value = true
  selectedDocument.value = null
  try {
    selectedDocument.value = await getDocument(knowledgeBaseId.value, item.id)
  } catch (requestError) {
    ElMessage.error(requestError instanceof Error ? requestError.message : '文档详情加载失败')
    documentDialog.value = false
  } finally {
    documentLoading.value = false
  }
}

async function removeDocument(item: DocumentListItem) {
  try {
    await ElMessageBox.confirm(`删除“${item.originalName}”及其索引数据吗？`, '删除文档', { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' })
    await deleteDocument(knowledgeBaseId.value, item.id)
    ElMessage.success('文档已删除')
    await load()
  } catch (requestError) {
    if (requestError !== 'cancel' && requestError !== 'close') ElMessage.error(requestError instanceof Error ? requestError.message : '删除文档失败')
  }
}

async function process(item: DocumentListItem) {
  busyAction.value = `process-${item.id}`
  try {
    const result = await processDocument(knowledgeBaseId.value, item.id)
    ElMessage.success(`处理完成：${result.chunkCount} 个 Chunk，${result.embeddingCount} 个向量`)
    await load()
  } catch (requestError) {
    ElMessage.error(requestError instanceof Error ? requestError.message : '文档处理失败')
    await load()
  } finally {
    busyAction.value = ''
  }
}

async function index(item: DocumentListItem) {
  busyAction.value = `index-${item.id}`
  try {
    const result = await indexDocument(knowledgeBaseId.value, item.id)
    ElMessage.success(`已写入 Qdrant：${result.pointCount} 个向量点`)
    await load()
  } catch (requestError) {
    ElMessage.error(requestError instanceof Error ? requestError.message : '向量索引失败')
    await load()
  } finally {
    busyAction.value = ''
  }
}

onMounted(load)
</script>

<template>
  <section class="page-stack detail-page">
    <div v-if="loading" class="detail-loading"><el-skeleton animated :rows="5" /></div>
    <template v-else>
      <el-alert v-if="error" :title="error" type="error" show-icon />
      <div class="detail-heading"><div><el-button text class="back-button" @click="router.push('/knowledge-bases')">← 返回知识库</el-button><span class="eyebrow">KNOWLEDGE BASE</span><h2>{{ knowledgeBase?.name || '知识库' }}</h2><p>{{ knowledgeBase?.description || '管理文档，构建可检索的知识上下文。' }}</p></div><div class="detail-count"><strong>{{ documents.length }}</strong><span>份文档</span></div></div>

      <section class="upload-card">
        <div class="upload-copy"><div class="upload-icon">↑</div><div><h3>添加知识文档</h3><p>上传 PDF、DOCX、Markdown 或 TXT，单文件不超过 20 MiB。</p></div></div>
        <div class="upload-actions"><input ref="fileInput" class="hidden-input" type="file" accept=".pdf,.docx,.md,.markdown,.txt" @change="chooseFile" /><el-button :disabled="uploading" @click="fileInput?.click()">选择文件</el-button><span v-if="selectedFile" class="selected-file">{{ selectedFile.name }} · {{ formatSize(selectedFile.size) }} <el-button text type="danger" @click="clearFile">×</el-button></span><el-button type="primary" :loading="uploading" :disabled="!selectedFile" @click="upload">{{ uploading ? `上传中 ${uploadProgress}%` : '开始上传' }}</el-button></div>
        <el-progress v-if="uploading" :percentage="uploadProgress" :show-text="false" status="success" />
      </section>

      <section class="content-card document-card"><div class="section-title-row"><div><span class="eyebrow">DOCUMENTS</span><h2>文档与索引</h2></div><el-button text :loading="loading" @click="load">刷新</el-button></div>
        <el-table v-if="documents.length" :data="documents" row-key="id" class="document-table">
          <el-table-column min-width="230" label="文档"><template #default="{ row }"><button class="document-name" @click="showDocument(row)"><span class="file-badge">{{ row.fileType?.toUpperCase() || 'FILE' }}</span><span><strong>{{ row.originalName }}</strong><small>{{ formatSize(row.fileSize) }} · {{ formatDate(row.createdAt) }}</small></span></button></template></el-table-column>
          <el-table-column min-width="130" label="解析状态"><template #default="{ row }"><el-tooltip v-if="row.processingError" :content="row.processingError"><el-tag :type="processingType(row.processingStatus)" size="small">{{ row.processingStatus }}</el-tag></el-tooltip><el-tag v-else :type="processingType(row.processingStatus)" size="small">{{ row.processingStatus }}</el-tag></template></el-table-column>
          <el-table-column min-width="130" label="向量索引"><template #default="{ row }"><el-tooltip v-if="row.indexingError" :content="row.indexingError"><el-tag :type="indexingType(row.indexingStatus)" size="small">{{ row.indexingStatus }}</el-tag></el-tooltip><el-tag v-else :type="indexingType(row.indexingStatus)" size="small">{{ row.indexingStatus }}</el-tag></template></el-table-column>
          <el-table-column fixed="right" width="220" label="操作"><template #default="{ row }"><el-button link type="primary" :loading="busyAction === `process-${row.id}`" :disabled="row.processingStatus === 'PROCESSING'" @click="process(row)">{{ row.processingStatus === 'PROCESSED' ? '重新处理' : '处理文档' }}</el-button><el-button link type="success" :loading="busyAction === `index-${row.id}`" :disabled="row.processingStatus !== 'PROCESSED' || row.indexingStatus === 'INDEXING'" @click="index(row)">{{ row.indexingStatus === 'INDEXED' ? '重新索引' : '建立索引' }}</el-button><el-button link type="danger" @click="removeDocument(row)">删除</el-button></template></el-table-column>
        </el-table>
        <el-empty v-else description="上传第一份文档，开始构建知识库" />
      </section>

      <ChatPanel :knowledge-base-id="knowledgeBaseId" />
    </template>

    <el-dialog v-model="documentDialog" title="文档详情" width="min(900px, 94vw)" top="6vh"><el-skeleton v-if="documentLoading" animated :rows="8" /><template v-else-if="selectedDocument"><div class="document-detail-meta"><strong>{{ selectedDocument.originalName }}</strong><span>{{ selectedDocument.fileType?.toUpperCase() }} · {{ formatSize(selectedDocument.fileSize) }} · {{ formatDate(selectedDocument.createdAt) }}</span></div><pre class="document-content">{{ selectedDocument.contentText || '文档没有可读取的正文内容。' }}</pre></template></el-dialog>
  </section>
</template>
