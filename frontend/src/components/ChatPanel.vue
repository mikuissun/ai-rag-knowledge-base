<script setup lang="ts">
import { nextTick, onUnmounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { streamChat } from '../api/rag'
import type { Citation } from '../types/api'

const props = defineProps<{ knowledgeBaseId: number }>()

interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
  sources: Citation[]
  error?: boolean
}

const question = ref('')
const topK = ref(5)
const messages = ref<ChatMessage[]>([])
const streaming = ref(false)
const messagesRef = ref<HTMLDivElement | null>(null)
let controller: AbortController | null = null
let activeAssistant: ChatMessage | null = null

function scrollToBottom() {
  void nextTick(() => {
    if (messagesRef.value) messagesRef.value.scrollTop = messagesRef.value.scrollHeight
  })
}

function ensureAssistant() {
  if (!activeAssistant) {
    activeAssistant = { role: 'assistant', content: '', sources: [] }
    messages.value.push(activeAssistant)
  }
  return activeAssistant
}

async function send() {
  const text = question.value.trim()
  if (!text || streaming.value) return
  question.value = ''
  messages.value.push({ role: 'user', content: text, sources: [] })
  activeAssistant = null
  streaming.value = true
  controller = new AbortController()
  scrollToBottom()

  try {
    await streamChat(props.knowledgeBaseId, { question: text, topK: topK.value }, {
      onMessage: (delta) => {
        if (!delta) return
        ensureAssistant().content += delta
        scrollToBottom()
      },
      onSources: (sources) => {
        if (sources.length) ensureAssistant().sources = sources
        scrollToBottom()
      },
      onDone: () => {
        streaming.value = false
        controller = null
        if (!activeAssistant?.content && activeAssistant) {
          messages.value = messages.value.filter((message) => message !== activeAssistant)
          activeAssistant = null
        }
        scrollToBottom()
      },
      onError: (message) => {
        if (activeAssistant) activeAssistant.error = true
        ElMessage.error(message)
        controller?.abort()
      },
    }, controller.signal)
  } catch (error) {
    if ((error as Error).name !== 'AbortError') {
      const assistant = activeAssistant as ChatMessage | null
      if (assistant !== null && assistant.content.length === 0) messages.value = messages.value.filter((message) => message !== assistant)
      else if (assistant !== null) assistant.error = true
      ElMessage.error(error instanceof Error ? error.message : '流式问答失败')
    }
  } finally {
    streaming.value = false
    controller = null
    activeAssistant = null
    scrollToBottom()
  }
}

function stop() {
  controller?.abort()
  streaming.value = false
}

function score(value: number) {
  return Number.isFinite(value) ? value.toFixed(3) : '-'
}

onUnmounted(() => controller?.abort())
</script>

<template>
  <section class="chat-panel">
    <div class="section-title-row"><div><span class="eyebrow">RAG CHAT</span><h2>向知识库提问</h2></div><el-tag type="info" effect="plain">TopK {{ topK }}</el-tag></div>
    <div ref="messagesRef" class="chat-messages">
      <div v-if="messages.length === 0" class="chat-empty"><div class="chat-spark">✦</div><strong>从一个问题开始</strong><p>回答会基于当前知识库中的文档，并附带可追溯引用。</p></div>
      <article v-for="(message, index) in messages" :key="`${index}-${message.role}`" :class="['chat-message', message.role]">
        <div class="message-avatar">{{ message.role === 'user' ? '我' : 'AI' }}</div>
        <div class="message-body"><div class="message-role">{{ message.role === 'user' ? '你' : 'Knowledge AI' }}</div><div :class="['message-content', { 'message-error': message.error }]">{{ message.content || '正在检索并生成回答…' }}</div>
          <div v-if="message.sources.length" class="citation-list"><div class="citation-heading">引用来源 · {{ message.sources.length }}</div><div v-for="source in message.sources" :key="`${source.documentId}-${source.chunkId}`" class="citation-item"><div><strong>{{ source.documentName }}</strong><span>Chunk {{ source.chunkIndex }} · 相似度 {{ score(source.score) }}</span></div><p>{{ source.contentSnippet }}</p></div></div>
        </div>
      </article>
    </div>
    <div class="chat-composer"><el-input v-model="question" type="textarea" :autosize="{ minRows: 1, maxRows: 4 }" resize="none" maxlength="4000" show-word-limit :disabled="streaming" placeholder="向你的知识库提问…" @keydown.enter.exact.prevent="send" /><div class="composer-actions"><span>Enter 发送 · Shift + Enter 换行</span><el-button v-if="streaming" type="danger" plain @click="stop">停止生成</el-button><el-button v-else type="primary" :disabled="!question.trim()" @click="send">发送 ↗</el-button></div></div>
  </section>
</template>
