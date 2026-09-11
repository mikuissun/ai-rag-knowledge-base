<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ status: string; kind: 'processing' | 'indexing'; error?: string | null }>()
const busy = computed(() => ['PROCESSING', 'INDEXING'].includes(props.status))
const label = computed(() => ({
  PENDING: props.kind === 'processing' ? '待处理' : '待索引',
  PROCESSING: '处理中', PROCESSED: '已处理', INDEXING: '索引中', INDEXED: '已建立索引', FAILED: '失败',
}[props.status] ?? '状态未知'))
const type = computed(() => {
  if (props.status === 'FAILED') return 'danger'
  if (busy.value) return 'warning'
  return ['PROCESSED', 'INDEXED'].includes(props.status) ? 'success' : 'info'
})
</script>

<template>
  <div class="document-status">
    <el-tag :type="type" effect="light" size="small">
      <span v-if="busy" class="busy-indicator" aria-hidden="true" />{{ label }}
    </el-tag>
    <span v-if="error && !busy" class="status-error" :title="error">{{ error }}</span>
  </div>
</template>
