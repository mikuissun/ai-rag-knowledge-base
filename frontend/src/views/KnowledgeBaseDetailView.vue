<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getKnowledgeBase } from '../api/knowledgeBase'
import { deleteDocument, getDocument, indexDocument, listDocuments, processDocument, uploadDocument } from '../api/document'
import ChatPanel from '../components/ChatPanel.vue'
import DocumentStatusTag from '../components/DocumentStatusTag.vue'
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
const uploadError = ref('')
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

function isBusy(item: DocumentListItem) {
  return busyAction.value !== '' || ['PROCESSING', 'INDEXING'].includes(item.processingStatus) || item.indexingStatus === 'INDEXING'
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
  uploadError.value = ''
  selectedFile.value = null
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  const extension = fileExtension(file.name)
  if (!acceptedExtensions.includes(extension)) {
    uploadError.value = '仅支持 PDF、DOCX、Markdown 和 TXT 文件'
    input.value = ''
    return
  }
  if (file.size === 0 || file.size > MAX_FILE_SIZE) {
    uploadError.value = file.size === 0 ? '不能上传空文件' : '文件大小不能超过 20 MiB'
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
  if (!selectedFile.value || uploading.value) return
  uploadError.value = ''
  uploading.value = true
  uploadProgress.value = 0
  try {
    await uploadDocument(knowledgeBaseId.value, selectedFile.value, (progress) => { uploadProgress.value = progress })
    ElMessage.success('文档上传并解析成功')
    clearFile()
    await load()
  } catch (requestError) {
    uploadError.value = requestError instanceof Error ? requestError.message : '文档上传失败'
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
  if (isBusy(item)) return
  busyAction.value = `delete-${item.id}`
  try {
    await ElMessageBox.confirm(`删除“${item.originalName}”及其索引数据吗？`, '删除文档', { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' })
    await deleteDocument(knowledgeBaseId.value, item.id)
    ElMessage.success('文档已删除')
    await load()
  } catch (requestError) {
    if (requestError !== 'cancel' && requestError !== 'close') ElMessage.error(requestError instanceof Error ? requestError.message : '删除文档失败')
  } finally {
    busyAction.value = ''
  }
}

async function process(item: DocumentListItem) {
  if (isBusy(item)) return
  busyAction.value = `process-${item.id}`
  try {
    const result = await processDocument(knowledgeBaseId.value, item.id)
    ElMessage.success(`处理完成：已生成 ${result.chunkCount} 个知识片段`)
    await load()
  } catch (requestError) {
    ElMessage.error(requestError instanceof Error ? requestError.message : '文档处理失败')
    await load()
  } finally {
    busyAction.value = ''
  }
}

async function index(item: DocumentListItem) {
  if (isBusy(item) || item.processingStatus !== 'PROCESSED') return
  busyAction.value = `index-${item.id}`
  try {
    const result = await indexDocument(knowledgeBaseId.value, item.id)
    ElMessage.success(`索引完成：${result.pointCount} 个片段可用于问答`)
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
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false"><el-button text @click="load">重新加载</el-button></el-alert>
    <div v-if="loading && !knowledgeBase" class="detail-loading"><el-skeleton animated :rows="5" /></div>
    <template v-if="knowledgeBase">

      <div class="detail-heading"><div><el-button text class="back-button" @click="router.push('/knowledge-bases')">← 返回知识库</el-button><span class="eyebrow">知识库</span><h2>{{ knowledgeBase?.name || '知识库' }}</h2><p>{{ knowledgeBase?.description || '上传资料，建立索引后即可向知识库提问。' }}</p></div><div class="detail-count"><strong>{{ documents.length }}</strong><span>份文档</span></div></div>

      <section class="upload-card" :aria-busy="uploading">
        <div class="upload-copy"><div class="upload-icon">↑</div><div><h3>添加知识文档</h3><p>上传 PDF、DOCX、Markdown 或 TXT，单文件不超过 20 MiB。</p></div></div>
        <div class="upload-actions"><input ref="fileInput" class="hidden-input" type="file" accept=".pdf,.docx,.md,.markdown,.txt" @change="chooseFile" /><el-button :disabled="uploading" @click="fileInput?.click()">选择文件</el-button><span v-if="selectedFile" class="selected-file">{{ selectedFile.name }} · {{ formatSize(selectedFile.size) }} <el-button text type="danger" :disabled="uploading" aria-label="移除所选文件" @click="clearFile">×</el-button></span><el-button type="primary" :loading="uploading" :disabled="!selectedFile" @click="upload">{{ uploading ? (uploadProgress === 100 ? '正在解析文档…' : `上传中 ${uploadProgress}%`) : '开始上传' }}</el-button></div>
        <el-alert v-if="uploadError" :title="uploadError" type="error" show-icon :closable="false" />
        <el-progress v-if="uploading" :percentage="uploadProgress" :show-text="false"  />
      </section>

      <section class="content-card document-card"><div class="section-title-row"><div><span class="eyebrow">资料管理</span><h2>文档</h2><p class="section-description">上传 → 处理文档 → 建立索引，即可用于知识问答。</p></div><el-button text :loading="loading" @click="load">刷新</el-button></div>
        <el-table v-if="documents.length" :data="documents" row-key="id" class="document-table">
          <el-table-column min-width="230" label="文档"><template #default="{ row }"><button class="document-name" @click="showDocument(row)"><span class="file-badge">{{ row.fileType?.toUpperCase() || 'FILE' }}</span><span><strong>{{ row.originalName }}</strong><small>{{ formatSize(row.fileSize) }} · {{ formatDate(row.createdAt) }}</small></span></button></template></el-table-column>
          <el-table-column min-width="140" label="处理状态"><template #default="{ row }"><DocumentStatusTag :status="busyAction === `process-${row.id}` ? 'PROCESSING' : row.processingStatus" kind="processing" :error="row.processingError" /></template></el-table-column>
          <el-table-column min-width="150" label="索引状态"><template #default="{ row }"><DocumentStatusTag :status="busyAction === `index-${row.id}` ? 'INDEXING' : row.indexingStatus" kind="indexing" :error="row.indexingError" /></template></el-table-column>
          <el-table-column width="270" label="操作"><template #default="{ row }"><el-button size="small" plain type="primary" :loading="busyAction === `process-${row.id}`" :disabled="isBusy(row)" @click="process(row)">{{ row.processingStatus === 'PROCESSED' ? '重新处理' : '处理文档' }}</el-button><el-button size="small" :loading="busyAction === `index-${row.id}`" :disabled="row.processingStatus !== 'PROCESSED' || isBusy(row)" :title="row.processingStatus !== 'PROCESSED' ? '请先处理文档' : '将文档加入可检索知识'" @click="index(row)">{{ row.indexingStatus === 'INDEXED' ? '重新索引' : '建立索引' }}</el-button><el-button link type="danger" :loading="busyAction === `delete-${row.id}`" :disabled="isBusy(row)" @click="removeDocument(row)">删除</el-button></template></el-table-column>
        </el-table>
        <el-empty v-else description="上传第一份文档，开始构建知识库" />
      </section>

      <ChatPanel :knowledge-base-id="knowledgeBaseId" />
    </template>

    <el-dialog v-model="documentDialog" title="文档详情" width="min(900px, 94vw)" top="6vh"><el-skeleton v-if="documentLoading" animated :rows="8" /><template v-else-if="selectedDocument"><div class="document-detail-meta"><strong>{{ selectedDocument.originalName }}</strong><span>{{ selectedDocument.fileType?.toUpperCase() }} · {{ formatSize(selectedDocument.fileSize) }} · {{ formatDate(selectedDocument.createdAt) }}</span></div><pre class="document-content">{{ selectedDocument.contentText || '文档没有可读取的正文内容。' }}</pre></template></el-dialog>
  </section>
</template>
