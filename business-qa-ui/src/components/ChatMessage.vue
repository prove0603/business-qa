<template>
  <div
    class="chat-message"
    :class="message.role === 'user' ? 'chat-message--user' : 'chat-message--assistant'"
  >
    <div v-if="message.role === 'user'" class="bubble bubble--user">
      {{ message.content }}
    </div>
    <div v-else class="bubble bubble--assistant" :class="{ 'bubble--guardrail': message.guardrailBlocked }">
      <div v-if="message.guardrailBlocked" class="guardrail-notice">
        <span class="guardrail-icon">🛡️</span>
        <span>{{ message.content }}</span>
      </div>
      <div v-else class="markdown-body" v-html="renderedHtml" />
      <el-collapse v-if="hasRefs" class="refs-collapse" accordion>
        <el-collapse-item name="refs">
          <template #title>
            <span class="refs-title">引用来源</span>
          </template>
          <ul class="refs-list">
            <li v-for="(ref, i) in message.sourceRefs" :key="i" class="refs-item">
              <div class="refs-doc">
                <span class="refs-doc-title">{{ ref.docTitle }}</span>
                <el-tag size="small" type="info" class="refs-module">{{ ref.moduleCode }}</el-tag>
              </div>
              <p class="refs-excerpt">{{ ref.excerpt }}</p>
            </li>
          </ul>
        </el-collapse-item>
      </el-collapse>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { marked } from 'marked'
import hljs from 'highlight.js'
import 'highlight.js/styles/github.css'

export interface SourceRef {
  docTitle: string
  moduleCode: string
  excerpt: string
}

export interface ChatMessageModel {
  role: 'user' | 'assistant'
  content: string
  sourceRefs?: SourceRef[]
  guardrailBlocked?: boolean
}

const props = defineProps<{
  message: ChatMessageModel
}>()

marked.use({
  breaks: true,
  gfm: true,
  renderer: {
    code({ text, lang }) {
      try {
        if (lang && hljs.getLanguage(lang)) {
          return `<pre><code class="hljs language-${lang}">${hljs.highlight(text, { language: lang }).value}</code></pre>`
        }
        return `<pre><code class="hljs">${hljs.highlightAuto(text).value}</code></pre>`
      } catch {
        return `<pre><code class="hljs">${(text || '')
          .replace(/&/g, '&amp;')
          .replace(/</g, '&lt;')
          .replace(/>/g, '&gt;')}</code></pre>`
      }
    },
  },
})

const renderedHtml = computed(() => {
  if (props.message.role !== 'assistant') return ''
  return marked.parse(props.message.content || '') as string
})

const hasRefs = computed(
  () =>
    props.message.role === 'assistant' &&
    Array.isArray(props.message.sourceRefs) &&
    props.message.sourceRefs.length > 0
)
</script>

<style scoped>
.chat-message {
  display: flex;
  width: 100%;
  margin-bottom: 16px;
}

.chat-message--user {
  justify-content: flex-end;
}

.chat-message--assistant {
  justify-content: flex-start;
}

.bubble {
  max-width: min(720px, 92%);
  border-radius: 12px;
  padding: 10px 14px;
  line-height: 1.55;
  font-size: 14px;
  word-break: break-word;
}

.bubble--user {
  background: var(--el-color-primary);
  color: #fff;
  border-bottom-right-radius: 4px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.06);
}

.bubble--assistant {
  background: var(--el-fill-color-blank);
  color: var(--el-text-color-primary);
  border: 1px solid var(--el-border-color-lighter);
  border-bottom-left-radius: 4px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
}

.bubble--guardrail {
  background: var(--el-color-warning-light-9);
  border-color: var(--el-color-warning-light-5);
}

.guardrail-notice {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  color: var(--el-color-warning-dark-2);
  font-size: 14px;
  line-height: 1.5;
}

.guardrail-icon {
  flex-shrink: 0;
  font-size: 16px;
}

.markdown-body :deep(p) {
  margin: 0 0 0.65em;
}

.markdown-body :deep(p:last-child) {
  margin-bottom: 0;
}

.markdown-body :deep(pre) {
  margin: 0.65em 0;
  padding: 12px 14px;
  border-radius: 8px;
  overflow-x: auto;
  background: #f6f8fa;
  border: 1px solid var(--el-border-color-extra-light);
}

.markdown-body :deep(code) {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New',
    monospace;
  font-size: 13px;
}

.markdown-body :deep(p code),
.markdown-body :deep(li code) {
  padding: 2px 6px;
  border-radius: 4px;
  background: rgba(175, 184, 193, 0.25);
}

.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  margin: 0.4em 0 0.65em;
  padding-left: 1.35em;
}

.markdown-body :deep(blockquote) {
  margin: 0.5em 0;
  padding-left: 12px;
  border-left: 3px solid var(--el-color-primary-light-5);
  color: var(--el-text-color-secondary);
}

.markdown-body :deep(table) {
  border-collapse: collapse;
  width: 100%;
  font-size: 13px;
  margin: 0.65em 0;
}

.markdown-body :deep(th),
.markdown-body :deep(td) {
  border: 1px solid var(--el-border-color);
  padding: 6px 10px;
}

.markdown-body :deep(a) {
  color: var(--el-color-primary);
}

.refs-collapse {
  margin-top: 10px;
  border: none;
  --el-collapse-header-bg-color: transparent;
}

.refs-collapse :deep(.el-collapse-item__header) {
  height: auto;
  line-height: 1.4;
  padding: 4px 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  border: none;
}

.refs-collapse :deep(.el-collapse-item__wrap) {
  border: none;
}

.refs-collapse :deep(.el-collapse-item__content) {
  padding: 0 0 4px;
}

.refs-title {
  font-weight: 500;
}

.refs-list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.refs-item {
  padding: 8px 10px;
  margin-bottom: 8px;
  background: var(--el-fill-color-light);
  border-radius: 8px;
  border: 1px solid var(--el-border-color-lighter);
}

.refs-item:last-child {
  margin-bottom: 0;
}

.refs-doc {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 6px;
}

.refs-doc-title {
  font-weight: 600;
  font-size: 13px;
  color: var(--el-text-color-primary);
}

.refs-module {
  flex-shrink: 0;
}

.refs-excerpt {
  margin: 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.45;
  white-space: pre-wrap;
}
</style>
