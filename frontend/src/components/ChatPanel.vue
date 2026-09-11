<script setup lang="ts">
import { nextTick, onUnmounted, reactive, ref } from 'vue'
import { streamChat } from '../api/rag'
import CitationList from './CitationList.vue'
import type { Citation } from '../types/api'

const props = defineProps<{ knowledgeBaseId: number }>()

interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
  sources: Citation[]
  state?: 'generating' | 'done' | 'stopped' | 'error'
  error?: string
}

const question = ref('')
const topK = 5
const messages = ref<ChatMessage[]>([])
const streaming = ref(false)
const messagesRef = ref<HTMLDivElement | null>(null)
const following = ref(true)
let controller: AbortController | null = null
let disposed = false

function trackScroll() {
  const element = messagesRef.value
  if (element) following.value = element.scrollHeight - element.scrollTop - element.clientHeight < 80
}

function scrollToBottom() {
  void nextTick(() => {
    if (following.value && messagesRef.value) messagesRef.value.scrollTop = messagesRef.value.scrollHeight
  })
}

async function send() {
  const text = question.value.trim()
  if (!text || streaming.value) return
  question.value = ''
  const assistant = reactive<ChatMessage>({ role: 'assistant', content: '', sources: [], state: 'generating' })
  messages.value.push({ role: 'user', content: text, sources: [] }, assistant)
  streaming.value = true
  const requestController = new AbortController()
  controller = requestController
  following.value = true
  scrollToBottom()

  try {
    await streamChat(props.knowledgeBaseId, { question: text, topK }, {
      onMessage: (delta) => {
        if (requestController.signal.aborted || disposed) return
        assistant.content += delta
        scrollToBottom()
      },
      onSources: (sources) => {
        if (requestController.signal.aborted || disposed) return
        assistant.sources = sources
        scrollToBottom()
      },
      onDone: () => {
        if (requestController.signal.aborted || disposed) return
        assistant.state = 'done'
        scrollToBottom()
      },
      onError: (message) => {
        if (disposed) return
        assistant.error = message
        assistant.state = 'error'
        requestController.abort()
      },
    }, requestController.signal)
    if (assistant.state === 'generating') {
      assistant.state = 'error'
      assistant.error = '连接提前结束，回答可能不完整，请重新提问。'
    }
  } catch (error) {
    if (assistant.state !== 'error') {
      if (requestController.signal.aborted) assistant.state = 'stopped'
      else {
        assistant.state = 'error'
        assistant.error = error instanceof Error ? error.message : '连接失败，请稍后重试。'
      }
    }
  } finally {
    if (controller === requestController) {
      streaming.value = false
      controller = null
    }
    scrollToBottom()
  }
}

function stop() {
  controller?.abort()
}

function handleEnter(event: KeyboardEvent) {
  if (event.key !== 'Enter' || event.shiftKey || event.isComposing) return
  event.preventDefault()
  void send()
}

onUnmounted(() => {
  disposed = true
  controller?.abort()
})
</script>

<template>
  <section class="chat-panel" aria-label="知识库问答">
    <div class="section-title-row">
      <div><span class="eyebrow">知识助手</span><h2>向知识库提问</h2><p class="section-description">基于当前资料回答，为你提供可核对的参考来源。</p></div>
      <el-tooltip content="每次最多检索 5 个知识片段"><el-tag type="info" effect="plain">参考片段上限 {{ topK }}</el-tag></el-tooltip>
    </div>
    <div ref="messagesRef" class="chat-messages" role="log" aria-label="问答记录" :aria-busy="streaming" @scroll="trackScroll">
      <div v-if="messages.length === 0" class="chat-empty">
        <div class="chat-spark" aria-hidden="true">AI</div><strong>你的资料，可以直接提问</strong>
        <p>先为文档建立索引，再询问政策、流程或资料中的具体内容。</p>
        <span>每次提问独立回答</span>
      </div>
      <article v-for="(message, index) in messages" :key="index" :class="['chat-message', message.role]">
        <div class="message-avatar" aria-hidden="true">{{ message.role === 'user' ? '我' : 'AI' }}</div>
        <div class="message-body">
          <div class="message-role">{{ message.role === 'user' ? '你' : '知识助手' }}</div>
          <div v-if="message.content" class="message-content">{{ message.content }}</div>
          <div v-if="message.state === 'generating'" class="generation-status"><span class="busy-indicator" />{{ message.content ? '正在生成回答…' : '正在查找资料并组织回答…' }}</div>
          <el-alert v-if="message.error" :title="message.error" type="error" show-icon :closable="false" />
          <div v-if="message.state === 'stopped'" class="generation-status">已停止生成{{ message.content ? '，已保留现有回答' : '' }}</div>
          <CitationList :sources="message.sources" />
          <p v-if="message.state === 'done' && !message.sources.length" class="no-sources">本次回答没有可展示的参考来源。</p>
        </div>
      </article>
    </div>
    <div class="chat-composer">
      <el-input v-model="question" aria-label="输入你的问题" type="textarea" :autosize="{ minRows: 2, maxRows: 5 }" resize="none" maxlength="4000" show-word-limit :disabled="streaming" placeholder="例如：这份资料中的退款条件是什么？" @keydown="handleEnter" />
      <div class="composer-actions">
        <span>Enter 发送 · Shift + Enter 换行</span>
        <el-button v-if="streaming" plain @click="stop">停止生成</el-button>
        <el-button v-else type="primary" :disabled="!question.trim()" @click="send">发送问题</el-button>
      </div>
    </div>
  </section>
</template>
