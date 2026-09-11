<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login } from '../api/auth'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const loading = ref(false)
const form = reactive({ username: typeof route.query.username === 'string' ? route.query.username : '', password: '' })

async function submit() {
  if (!form.username || !form.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    const result = await login(form)
    auth.setSession(result.token, result.user)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/knowledge-bases'
    await router.replace(redirect)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="auth-page">
    <section class="auth-visual">
      <div class="visual-orbit orbit-one" /><div class="visual-orbit orbit-two" />
      <span class="visual-label">AI KNOWLEDGE WORKSPACE</span>
      <h1>让企业知识<br /><em>真正可被理解。</em></h1>
      <p>统一管理文档、检索知识与生成可信回答，让每一次对话都有依据。</p>
      <div class="visual-metric"><strong>RAG</strong><span>Retrieval · Context · Answer</span></div>
    </section>
    <section class="auth-panel">
      <div class="auth-panel-inner">
        <div class="mobile-brand"><span class="brand-mark">KB</span> Knowledge Hub</div>
        <span class="eyebrow">欢迎回来</span>
        <h2>登录工作区</h2>
        <p class="auth-subtitle">使用你的账号继续管理知识库。</p>
        <el-form :model="form" label-position="top" @submit.prevent="submit">
          <el-form-item label="用户名"><el-input v-model="form.username" size="large" autocomplete="username" placeholder="输入用户名" /></el-form-item>
          <el-form-item label="密码"><el-input v-model="form.password" size="large" type="password" show-password autocomplete="current-password" placeholder="输入密码" @keyup.enter="submit" /></el-form-item>
          <el-button class="full-button" type="primary" size="large" :loading="loading" @click="submit">登录</el-button>
        </el-form>
        <p class="auth-switch">还没有账号？<RouterLink to="/register">创建账号</RouterLink></p>
      </div>
    </section>
  </main>
</template>
