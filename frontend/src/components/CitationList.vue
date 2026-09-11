<script setup lang="ts">
import type { Citation } from '../types/api'
defineProps<{ sources: Citation[] }>()

function score(value: number) {
  return Number.isFinite(value) ? `${Math.round(value * 100)}%` : '—'
}
</script>

<template>
  <section v-if="sources.length" class="citation-list" aria-label="参考来源">
    <div class="citation-heading">参考来源 <span>{{ sources.length }}</span></div>
    <div class="citation-grid">
      <article v-for="(source, index) in sources" :key="`${source.documentId}-${source.chunkId}`" class="citation-item">
        <div class="citation-title"><span class="source-number">{{ index + 1 }}</span><strong :title="source.documentName">{{ source.documentName }}</strong></div>
        <div class="citation-meta"><span>片段 {{ source.chunkIndex }}</span><span>相似度 {{ score(source.score) }}</span></div>
        <p>{{ source.contentSnippet || '此来源未提供摘要。' }}</p>
      </article>
    </div>
  </section>
</template>
