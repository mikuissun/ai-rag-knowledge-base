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
const formError = ref('')
const form = reactive({ username: typeof route.query.username === 'string' ? route.query.username : '', password: '' })

async function submit() {
  if (loading.value) return
  formError.value = ''
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
    formError.value = error instanceof Error ? error.message : '登录失败'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="auth-page">
    <section class="auth-visual">
      <span class="brand-mark">KB</span>
      <span class="visual-label">企业知识工作区</span>
      <h1>让知识有序，<br /><em>让回答有据。</em></h1>
      <p>集中管理资料，通过对话找到有依据的答案。</p>
      <div class="visual-metric"><span>文档管理</span><span>知识问答</span><span>来源可追溯</span></div>
    </section>
    <section class="auth-panel">
      <div class="auth-panel-inner">
        <div class="mobile-brand"><span class="brand-mark">KB</span> Knowledge Hub</div>
        <span class="eyebrow">欢迎回来</span>
        <h2>登录工作区</h2>
        <p class="auth-subtitle">使用你的账号继续管理知识库。</p>
        <el-alert v-if="formError" :title="formError" type="error" show-icon :closable="false" class="form-error" />
        <el-form :disabled="loading" :model="form" label-position="top" @submit.prevent="submit">
          <el-form-item label="用户名"><el-input v-model="form.username" size="large" autocomplete="username" placeholder="输入用户名" /></el-form-item>
          <el-form-item label="密码"><el-input v-model="form.password" size="large" type="password" show-password autocomplete="current-password" placeholder="输入密码" /></el-form-item>
          <el-button class="full-button" type="primary" size="large" :loading="loading" native-type="submit">登录</el-button>
        </el-form>
        <p class="auth-switch">还没有账号？<RouterLink to="/register">创建账号</RouterLink></p>
      </div>
    </section>
  </main>
</template>
