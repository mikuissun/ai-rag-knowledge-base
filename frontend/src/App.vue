<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { login, registerUser, type UserProfile } from './api/auth'
import { createKnowledgeBase, deleteKnowledgeBase, listKnowledgeBases, updateKnowledgeBase, type KnowledgeBase } from './api/knowledge-base'

type Mode = 'login' | 'register'

const mode = ref<Mode>('login')
const submitting = ref(false)
const message = ref('')
const error = ref('')
const currentUser = ref<UserProfile | null>(null)
const knowledgeBases = ref<KnowledgeBase[]>([])
const selectedKnowledgeBase = ref<KnowledgeBase | null>(null)
const knowledgeBaseForm = reactive({ name: '', description: '' })

const loginForm = reactive({ username: '', password: '' })
const registerForm = reactive({ username: '', password: '', nickname: '', email: '' })

function switchMode(nextMode: Mode) {
  mode.value = nextMode
  message.value = ''
  error.value = ''
}

function token() {
  return localStorage.getItem('knowledge-base-token')
}

async function loadKnowledgeBases() {
  const savedToken = token()
  if (!savedToken) return
  try {
    knowledgeBases.value = await listKnowledgeBases(savedToken)
  } catch (requestError) {
    error.value = requestError instanceof Error ? requestError.message : '加载知识库失败'
  }
}

function selectKnowledgeBase(knowledgeBase: KnowledgeBase) {
  selectedKnowledgeBase.value = knowledgeBase
  knowledgeBaseForm.name = knowledgeBase.name
  knowledgeBaseForm.description = knowledgeBase.description || ''
}

function resetKnowledgeBaseForm() {
  selectedKnowledgeBase.value = null
  knowledgeBaseForm.name = ''
  knowledgeBaseForm.description = ''
}

async function saveKnowledgeBase() {
  const savedToken = token()
  if (!savedToken) return
  submitting.value = true
  error.value = ''
  try {
    if (selectedKnowledgeBase.value) {
      await updateKnowledgeBase(savedToken, selectedKnowledgeBase.value.id, knowledgeBaseForm)
      message.value = '知识库已更新。'
    } else {
      await createKnowledgeBase(savedToken, knowledgeBaseForm)
      message.value = '知识库已创建。'
    }
    resetKnowledgeBaseForm()
    await loadKnowledgeBases()
  } catch (requestError) {
    error.value = requestError instanceof Error ? requestError.message : '保存知识库失败'
  } finally {
    submitting.value = false
  }
}

async function removeKnowledgeBase(knowledgeBase: KnowledgeBase) {
  const savedToken = token()
  if (!savedToken || !window.confirm(`确定删除“${knowledgeBase.name}”吗？`)) return
  try {
    await deleteKnowledgeBase(savedToken, knowledgeBase.id)
    if (selectedKnowledgeBase.value?.id === knowledgeBase.id) resetKnowledgeBaseForm()
    await loadKnowledgeBases()
    message.value = '知识库已删除。'
  } catch (requestError) {
    error.value = requestError instanceof Error ? requestError.message : '删除知识库失败'
  }
}

function logout() {
  localStorage.removeItem('knowledge-base-token')
  localStorage.removeItem('knowledge-base-user')
  currentUser.value = null
  knowledgeBases.value = []
  resetKnowledgeBaseForm()
  switchMode('login')
}

async function submitLogin() {
  submitting.value = true
  message.value = ''
  error.value = ''
  try {
    const result = await login(loginForm)
    localStorage.setItem('knowledge-base-token', result.token)
    localStorage.setItem('knowledge-base-user', JSON.stringify(result.user))
    currentUser.value = result.user
    message.value = `欢迎回来，${result.user.nickname || result.user.username}`
    await loadKnowledgeBases()
  } catch (requestError) {
    error.value = requestError instanceof Error ? requestError.message : '登录失败'
  } finally {
    submitting.value = false
  }
}

async function submitRegister() {
  submitting.value = true
  message.value = ''
  error.value = ''
  try {
    const user = await registerUser(registerForm)
    message.value = `用户 ${user.username} 注册成功，请登录。`
    loginForm.username = user.username
    loginForm.password = ''
    mode.value = 'login'
  } catch (requestError) {
    error.value = requestError instanceof Error ? requestError.message : '注册失败'
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  const savedUser = localStorage.getItem('knowledge-base-user')
  if (token() && savedUser) {
    try {
      currentUser.value = JSON.parse(savedUser) as UserProfile
      void loadKnowledgeBases()
    } catch {
      logout()
    }
  }
})
</script>

<template>
  <main class="auth-page">
    <section class="auth-card">
      <p class="eyebrow">STAGE 3 · KNOWLEDGE BASES</p>
      <h1>企业级 AI 知识库问答系统</h1>
      <p class="description">使用账号登录后，后续可访问个人知识库与智能问答功能。</p>

      <template v-if="currentUser">
        <div class="user-bar">
          <span>当前用户：<strong>{{ currentUser.nickname || currentUser.username }}</strong></span>
          <button class="text-button" type="button" @click="logout">退出登录</button>
        </div>
        <div class="knowledge-layout">
          <section>
            <div class="section-heading"><h2>我的知识库</h2><button class="text-button" type="button" @click="resetKnowledgeBaseForm">新建</button></div>
            <p v-if="knowledgeBases.length === 0" class="empty">还没有知识库，先创建一个吧。</p>
            <ul v-else class="knowledge-list">
              <li v-for="knowledgeBase in knowledgeBases" :key="knowledgeBase.id">
                <button class="knowledge-item" type="button" @click="selectKnowledgeBase(knowledgeBase)">
                  <strong>{{ knowledgeBase.name }}</strong><span>{{ knowledgeBase.description || '暂无描述' }}</span>
                </button>
                <button class="danger-button" type="button" @click="removeKnowledgeBase(knowledgeBase)">删除</button>
              </li>
            </ul>
          </section>
          <form class="knowledge-form" @submit.prevent="saveKnowledgeBase">
            <h2>{{ selectedKnowledgeBase ? '编辑知识库' : '创建知识库' }}</h2>
            <label>名称<input v-model.trim="knowledgeBaseForm.name" required maxlength="100" /></label>
            <label>描述（可选）<textarea v-model.trim="knowledgeBaseForm.description" maxlength="500" rows="4" /></label>
            <button class="primary" :disabled="submitting" type="submit">{{ submitting ? '保存中…' : '保存' }}</button>
          </form>
        </div>
      </template>

      <template v-else>
        <div class="tabs">
          <button :class="{ active: mode === 'login' }" type="button" @click="switchMode('login')">登录</button>
          <button :class="{ active: mode === 'register' }" type="button" @click="switchMode('register')">注册</button>
        </div>

        <form v-if="mode === 'login'" @submit.prevent="submitLogin">
          <label>用户名<input v-model.trim="loginForm.username" required autocomplete="username" /></label>
          <label>密码<input v-model="loginForm.password" required type="password" autocomplete="current-password" /></label>
          <button class="primary" :disabled="submitting" type="submit">{{ submitting ? '登录中…' : '登录' }}</button>
        </form>

        <form v-else @submit.prevent="submitRegister">
          <label>用户名<input v-model.trim="registerForm.username" required minlength="3" maxlength="50" autocomplete="username" /></label>
          <label>密码<input v-model="registerForm.password" required minlength="6" maxlength="72" type="password" autocomplete="new-password" /></label>
          <label>昵称（可选）<input v-model.trim="registerForm.nickname" maxlength="50" /></label>
          <label>邮箱（可选）<input v-model.trim="registerForm.email" type="email" maxlength="100" autocomplete="email" /></label>
          <button class="primary" :disabled="submitting" type="submit">{{ submitting ? '注册中…' : '创建账号' }}</button>
        </form>
      </template>

      <p v-if="message" class="notice success">{{ message }}</p>
      <p v-if="error" class="notice error">{{ error }}</p>
    </section>
  </main>
</template>
