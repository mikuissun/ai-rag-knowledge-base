<script setup lang="ts">
const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'
</script>

<template>
  <main class="home-page">
    <section class="hero">
      <p class="eyebrow">STAGE 1 · PROJECT INITIALIZATION</p>
      <h1>企业级 AI 知识库问答系统</h1>
      <p class="description">
        工程初始化已完成。用户登录、知识库、文档解析与 RAG 问答将在后续阶段逐步实现。
      </p>
      <p class="api-tip">后端 API 地址：{{ apiBaseUrl }}</p>
    </section>
  </main>
</template>
