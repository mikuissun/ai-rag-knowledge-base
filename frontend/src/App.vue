<script setup lang="ts">
import { reactive, ref } from 'vue'
import { login, registerUser, type UserProfile } from './api/auth'

type Mode = 'login' | 'register'

const mode = ref<Mode>('login')
const submitting = ref(false)
const message = ref('')
const error = ref('')
const currentUser = ref<UserProfile | null>(null)

const loginForm = reactive({ username: '', password: '' })
const registerForm = reactive({ username: '', password: '', nickname: '', email: '' })

function switchMode(nextMode: Mode) {
  mode.value = nextMode
  message.value = ''
  error.value = ''
}

async function submitLogin() {
  submitting.value = true
  message.value = ''
  error.value = ''
  try {
    const result = await login(loginForm)
    localStorage.setItem('knowledge-base-token', result.token)
    currentUser.value = result.user
    message.value = `欢迎回来，${result.user.nickname || result.user.username}`
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
</script>

<template>
  <main class="auth-page">
    <section class="auth-card">
      <p class="eyebrow">STAGE 2 · AUTHENTICATION</p>
      <h1>企业级 AI 知识库问答系统</h1>
      <p class="description">使用账号登录后，后续可访问个人知识库与智能问答功能。</p>

      <template v-if="currentUser">
        <div class="success-panel">
          <strong>{{ currentUser.nickname || currentUser.username }}</strong>
          <span>登录状态已保存在本地浏览器中。</span>
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
