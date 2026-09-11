<script setup lang="ts">
import { computed, onMounted, onUnmounted } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from './stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const showShell = computed(() => Boolean(route.meta.requiresAuth))

function logout() {
  auth.clearSession()
  void router.replace({ name: 'login' })
}

function onUnauthorized() {
  if (route.name !== 'login') ElMessage.warning('登录状态已失效，请重新登录')
}

onMounted(() => window.addEventListener('knowledge-base:unauthorized', onUnauthorized))
onUnmounted(() => window.removeEventListener('knowledge-base:unauthorized', onUnauthorized))
</script>

<template>
  <div v-if="showShell" class="app-shell">
    <el-container class="app-container">
      <el-aside width="224px" class="app-aside">
        <div class="brand-block">
          <span class="brand-mark">KB</span>
          <div><strong>Knowledge Hub</strong><small>企业知识库</small></div>
        </div>
        <nav class="side-nav">
          <RouterLink to="/knowledge-bases" :class="['side-link', { active: showShell }]"><span aria-hidden="true">▦</span> 知识库</RouterLink>
        </nav>
        <div class="aside-footer">
          <div class="account-chip">
            <el-avatar :size="34">{{ (auth.state.user?.nickname || auth.state.user?.username || 'U').slice(0, 1).toUpperCase() }}</el-avatar>
            <div><strong>{{ auth.state.user?.nickname || auth.state.user?.username }}</strong><small>{{ auth.state.user?.email || '本地工作区' }}</small></div>
          </div>
          <el-button text class="logout-button" @click="logout">退出登录</el-button>
        </div>
      </el-aside>
      <el-container>
        <el-header class="app-header">
          <div><span class="header-kicker">知识工作区</span><h1>{{ route.name === 'knowledge-base-detail' ? '知识库详情' : '我的知识库' }}</h1></div>
          <div class="header-status">个人知识空间</div>
        </el-header>
        <el-main class="app-main"><RouterView :key="route.path" /></el-main>
      </el-container>
    </el-container>
  </div>
  <RouterView v-else />
</template>
