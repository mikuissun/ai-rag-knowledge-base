<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createKnowledgeBase, deleteKnowledgeBase, listKnowledgeBases, updateKnowledgeBase } from '../api/knowledgeBase'
import type { KnowledgeBase, KnowledgeBaseForm } from '../types/api'

const router = useRouter()
const loading = ref(true)
const saving = ref(false)
const error = ref('')
const knowledgeBases = ref<KnowledgeBase[]>([])
const dialogVisible = ref(false)
const editing = ref<KnowledgeBase | null>(null)
const form = reactive<KnowledgeBaseForm>({ name: '', description: '' })

function formatDate(value: string) {
  return new Date(value).toLocaleDateString('zh-CN', { year: 'numeric', month: 'short', day: 'numeric' })
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    knowledgeBases.value = await listKnowledgeBases()
  } catch (requestError) {
    error.value = requestError instanceof Error ? requestError.message : '知识库加载失败'
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editing.value = null
  form.name = ''
  form.description = ''
  dialogVisible.value = true
}

function openEdit(item: KnowledgeBase) {
  editing.value = item
  form.name = item.name
  form.description = item.description || ''
  dialogVisible.value = true
}

async function save() {
  if (!form.name.trim()) {
    ElMessage.warning('请输入知识库名称')
    return
  }
  saving.value = true
  try {
    if (editing.value) await updateKnowledgeBase(editing.value.id, { name: form.name.trim(), description: form.description.trim() })
    else await createKnowledgeBase({ name: form.name.trim(), description: form.description.trim() })
    dialogVisible.value = false
    ElMessage.success(editing.value ? '知识库已更新' : '知识库已创建')
    await load()
  } catch (requestError) {
    ElMessage.error(requestError instanceof Error ? requestError.message : '保存失败')
  } finally {
    saving.value = false
  }
}

async function remove(item: KnowledgeBase) {
  try {
    await ElMessageBox.confirm(`删除“${item.name}”后将无法恢复，确认继续吗？`, '删除知识库', { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' })
    await deleteKnowledgeBase(item.id)
    ElMessage.success('知识库已删除')
    await load()
  } catch (requestError) {
    if (requestError !== 'cancel' && requestError !== 'close') ElMessage.error(requestError instanceof Error ? requestError.message : '删除失败')
  }
}

function openDetail(item: KnowledgeBase) {
  void router.push({ name: 'knowledge-base-detail', params: { id: item.id } })
}

onMounted(load)
</script>

<template>
  <section class="page-stack">
    <div class="page-intro">
      <div><span class="eyebrow">PERSONAL KNOWLEDGE BASES</span><h2>把零散资料，整理成可对话的知识。</h2><p>创建一个知识库，上传资料并开始构建你的专属 AI 工作区。</p></div>
      <el-button type="primary" size="large" @click="openCreate">＋ 新建知识库</el-button>
    </div>

    <el-alert v-if="error" :title="error" type="error" show-icon closable @close="error = ''" />
    <div v-if="loading" class="card-grid"><el-skeleton v-for="i in 3" :key="i" animated class="skeleton-card" /></div>
    <el-empty v-else-if="knowledgeBases.length === 0" description="还没有知识库" class="empty-card"><el-button type="primary" @click="openCreate">创建第一个知识库</el-button></el-empty>
    <div v-else class="card-grid">
      <el-card v-for="item in knowledgeBases" :key="item.id" class="knowledge-card" shadow="never" @click="openDetail(item)">
        <div class="card-icon">⌁</div>
        <div class="card-topline"><el-tag size="small" type="success" effect="light">已启用</el-tag><el-dropdown trigger="click" @click.stop>
          <el-button text class="more-button" @click.stop>•••</el-button>
          <template #dropdown><el-dropdown-menu><el-dropdown-item @click.stop="openEdit(item)">编辑</el-dropdown-item><el-dropdown-item divided @click.stop="remove(item)">删除</el-dropdown-item></el-dropdown-menu></template>
        </el-dropdown></div>
        <h3>{{ item.name }}</h3><p>{{ item.description || '暂未添加描述' }}</p>
        <div class="card-meta"><span>创建于 {{ formatDate(item.createdAt) }}</span><span class="arrow">→</span></div>
      </el-card>
    </div>

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑知识库' : '新建知识库'" width="min(520px, 92vw)" destroy-on-close>
      <el-form :model="form" label-position="top" @submit.prevent="save">
        <el-form-item label="名称"><el-input v-model="form.name" maxlength="100" show-word-limit /></el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" type="textarea" :rows="4" maxlength="500" show-word-limit /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template>
    </el-dialog>
  </section>
</template>
