<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { registerUser } from '../api/auth'

const router = useRouter()
const loading = ref(false)
const form = reactive({ username: '', password: '', nickname: '', email: '' })

async function submit() {
  if (form.username.length < 3 || form.password.length < 6) {
    ElMessage.warning('用户名至少 3 位，密码至少 6 位')
    return
  }
  loading.value = true
  try {
    await registerUser({ ...form, nickname: form.nickname || undefined, email: form.email || undefined })
    ElMessage.success('注册成功，请登录')
    await router.replace({ name: 'login', query: { username: form.username } })
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '注册失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="auth-page">
    <section class="auth-panel auth-panel-centered">
      <div class="auth-panel-inner">
        <div class="mobile-brand"><span class="brand-mark">KB</span> Knowledge Hub</div>
        <span class="eyebrow">开始建立你的知识库</span>
        <h2>创建账号</h2>
        <p class="auth-subtitle">注册后即可创建私有知识库并上传文档。</p>
        <el-form :model="form" label-position="top" @submit.prevent="submit">
          <el-form-item label="用户名"><el-input v-model="form.username" size="large" autocomplete="username" placeholder="3-50 位字母、数字或下划线" /></el-form-item>
          <el-form-item label="密码"><el-input v-model="form.password" size="large" type="password" show-password autocomplete="new-password" placeholder="至少 6 位" /></el-form-item>
          <el-form-item label="昵称（可选）"><el-input v-model="form.nickname" size="large" maxlength="50" placeholder="如何称呼你" /></el-form-item>
          <el-form-item label="邮箱（可选）"><el-input v-model="form.email" size="large" type="email" autocomplete="email" placeholder="name@example.com" /></el-form-item>
          <el-button class="full-button" type="primary" size="large" :loading="loading" @click="submit">创建账号</el-button>
        </el-form>
        <p class="auth-switch">已有账号？<RouterLink to="/login">返回登录</RouterLink></p>
      </div>
    </section>
  </main>
</template>
