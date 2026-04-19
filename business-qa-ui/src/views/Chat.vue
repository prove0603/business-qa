<template>
  <div class="chat-page">
    <aside class="chat-sidebar">
      <el-button type="primary" class="new-chat-btn" :icon="Plus" @click="onNewChat">
        新建对话
      </el-button>
      <div class="session-list">
        <div
          v-for="s in sessions"
          :key="s.id"
          class="session-item"
          :class="{ active: s.id === currentSessionId }"
          @click="selectSession(s.id)"
        >
          <span class="session-title">{{ s.title || '未命名' }}</span>
          <el-icon
            class="session-delete-btn"
            @click.stop="confirmDeleteSession(s.id, s.title)"
          >
            <Delete />
          </el-icon>
        </div>
        <el-empty v-if="!sessions.length && !sessionsLoading" description="暂无对话历史" :image-size="64" />
      </div>
    </aside>

    <section class="chat-main">
      <div class="module-filter">
        <div class="module-filter-label">模块筛选</div>
        <div class="module-checkboxes">
          <div v-for="m in commonModules" :key="'c-' + m.id" class="module-row">
            <el-checkbox :model-value="true" disabled border size="small">
              {{ m.moduleName || m.moduleCode }} <span class="type-tag">通用</span>
            </el-checkbox>
          </div>
          <el-checkbox-group v-if="taskModules.length" v-model="selectedTaskModuleIds" size="small">
            <div v-for="m in taskModules" :key="'t-' + m.id" class="module-row">
              <el-checkbox :label="m.id" border>
                {{ m.moduleName || m.moduleCode }} <span class="type-tag type-tag--task">任务</span>
              </el-checkbox>
            </div>
          </el-checkbox-group>
        </div>
      </div>

      <div ref="scrollRef" class="chat-scroll">
        <div v-if="!messages.length && !messagesLoading" class="empty-chat">
          <p class="empty-title">业务问答</p>
          <p class="empty-hint">如需任务模块，请先勾选后再提问。</p>
        </div>
        <ChatMessage v-for="(msg, i) in messages" :key="i" :message="msg" />
        <div v-if="sending" class="typing-hint">
          <el-icon class="spin"><Loading /></el-icon>
          <span>生成中…</span>
        </div>
      </div>

      <div class="chat-composer">
        <el-input
          v-model="input"
          type="textarea"
          :rows="3"
          :disabled="sending"
          placeholder="输入你的问题…"
          resize="none"
          @keydown="onKeydown"
        />
        <div class="composer-actions">
          <el-button type="primary" :loading="sending" :disabled="!canSend" @click="send">
            发送
          </el-button>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Loading, Plus } from '@element-plus/icons-vue'
import { chatApi, moduleApi } from '../api'
import ChatMessage, { type ChatMessageModel, type SourceRef } from '../components/ChatMessage.vue'

interface QaModuleRow {
  id: number
  moduleName?: string
  moduleCode?: string
  moduleType?: string
}

interface ChatSessionRow {
  id: number
  title?: string
}

interface RawChatMessage {
  role: string
  content?: string
  sourceRefs?: string | SourceRef[]
}

const sessions = ref<ChatSessionRow[]>([])
const sessionsLoading = ref(false)
const currentSessionId = ref<number | null>(null)

const modules = ref<QaModuleRow[]>([])
const selectedTaskModuleIds = ref<number[]>([])

const messages = ref<ChatMessageModel[]>([])
const messagesLoading = ref(false)

const input = ref('')
const sending = ref(false)
const scrollRef = ref<HTMLElement | null>(null)

const commonModules = computed(() => modules.value.filter((m) => m.moduleType === 'COMMON'))
const taskModules = computed(() => modules.value.filter((m) => m.moduleType === 'TASK'))

const canSend = computed(() => input.value.trim().length > 0 && !sending.value)

function parseSourceRefs(raw: RawChatMessage['sourceRefs']): SourceRef[] | undefined {
  if (raw == null) return undefined
  if (Array.isArray(raw)) return raw
  try {
    const parsed = JSON.parse(raw) as unknown
    return Array.isArray(parsed) ? (parsed as SourceRef[]) : undefined
  } catch {
    return undefined
  }
}

function normalizeMessage(m: RawChatMessage): ChatMessageModel {
  const role = m.role === 'user' ? 'user' : 'assistant'
  return {
    role,
    content: m.content ?? '',
    sourceRefs: role === 'assistant' ? parseSourceRefs(m.sourceRefs) : undefined,
  }
}

async function loadModules() {
  try {
    const data = (await moduleApi.list()) as unknown as QaModuleRow[]
    modules.value = Array.isArray(data) ? data : []
  } catch {
    ElMessage.error('模块加载失败')
  }
}

async function loadSessions() {
  sessionsLoading.value = true
  try {
    const data = (await chatApi.listSessions(50)) as unknown as ChatSessionRow[]
    sessions.value = Array.isArray(data) ? data : []
  } catch {
    ElMessage.error('对话历史加载失败')
  } finally {
    sessionsLoading.value = false
  }
}

async function loadMessages(sessionId: number) {
  messagesLoading.value = true
  try {
    const data = (await chatApi.getMessages(sessionId)) as unknown as RawChatMessage[]
    messages.value = Array.isArray(data) ? data.map(normalizeMessage) : []
    await scrollToBottom()
  } catch {
    ElMessage.error('消息加载失败')
  } finally {
    messagesLoading.value = false
  }
}

function onNewChat() {
  currentSessionId.value = null
  messages.value = []
  input.value = ''
}

async function selectSession(id: number) {
  if (id === currentSessionId.value) return
  currentSessionId.value = id
  await loadMessages(id)
}

async function confirmDeleteSession(id: number, title?: string) {
  try {
    await ElMessageBox.confirm(
      `确定要删除对话「${title || '未命名'}」吗？对话中的所有消息将一并删除且无法恢复。`,
      '删除对话',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await chatApi.deleteSession(id)
    ElMessage.success('对话已删除')
    if (currentSessionId.value === id) {
      currentSessionId.value = null
      messages.value = []
    }
    await loadSessions()
  } catch {
    ElMessage.error('删除失败')
  }
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    send()
  }
}

async function scrollToBottom() {
  await nextTick()
  const el = scrollRef.value
  if (el) {
    el.scrollTop = el.scrollHeight
  }
}

watch(
  () => messages.value.length,
  () => {
    scrollToBottom()
  }
)

/** Spring WebFlux SSE: lines `data: ...` separated by blank lines */
async function readSseTextStream(
  reader: ReadableStreamDefaultReader<Uint8Array>,
  onChunk: (text: string) => void
): Promise<void> {
  const decoder = new TextDecoder()
  let buffer = ''
  while (true) {
    const { done, value } = await reader.read()
    if (value) {
      buffer += decoder.decode(value, { stream: true })
    }
    let sep: number
    while ((sep = buffer.indexOf('\n\n')) !== -1) {
      const block = buffer.slice(0, sep)
      buffer = buffer.slice(sep + 2)
      const lines = block.split('\n').filter(Boolean)
      const dataLines = lines.filter((l) => l.startsWith('data:'))
      if (dataLines.length) {
        const text = dataLines.map((l) => l.slice(5).trimStart()).join('\n')
        onChunk(text)
      }
    }
    if (done) {
      if (buffer.trim()) {
        const lines = buffer.split('\n').filter(Boolean)
        for (const line of lines) {
          if (line.startsWith('data:')) {
            onChunk(line.slice(5).trimStart())
          }
        }
      }
      break
    }
  }
}

async function send() {
  const question = input.value.trim()
  if (!question || sending.value) return

  const sessionId = currentSessionId.value
  const moduleIds = [...selectedTaskModuleIds.value]

  messages.value.push({ role: 'user', content: question })
  const assistantIndex = messages.value.length
  messages.value.push({ role: 'assistant', content: '' })

  input.value = ''
  sending.value = true
  await scrollToBottom()

  let activeSessionId = sessionId
  const isNewSession = sessionId == null

  try {
    const response = await fetch('/api/chat/stream', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        question,
        moduleIds,
        sessionId: sessionId ?? undefined,
      }),
    })

    if (!response.ok) {
      const errText = await response.text().catch(() => '')
      throw new Error(errText || `请求失败（HTTP ${response.status}）`)
    }

    const body = response.body
    if (!body) {
      throw new Error('响应体为空')
    }

    const sessionPattern = /^\[SESSION:(\d+)]$/
    const guardrailPattern = /^\[GUARDRAIL](.+)$/
    const refsPattern = /^\[REFS:(.+)]$/
    await readSseTextStream(body.getReader(), (text) => {
      const sessionMatch = sessionPattern.exec(text)
      if (sessionMatch) {
        activeSessionId = Number(sessionMatch[1])
        currentSessionId.value = activeSessionId
        return
      }
      const guardrailMatch = guardrailPattern.exec(text)
      if (guardrailMatch) {
        const last = messages.value[assistantIndex]
        if (last && last.role === 'assistant') {
          last.content = guardrailMatch[1]
          last.guardrailBlocked = true
        }
        return
      }
      const refsMatch = refsPattern.exec(text)
      if (refsMatch) {
        try {
          const refs = JSON.parse(refsMatch[1]) as SourceRef[]
          const last = messages.value[assistantIndex]
          if (last && last.role === 'assistant') {
            last.sourceRefs = refs
          }
        } catch { /* ignore parse errors */ }
        return
      }
      const last = messages.value[assistantIndex]
      if (last && last.role === 'assistant') {
        last.content += text
      }
    })

    await loadSessions()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '流式响应失败')
    messages.value.splice(assistantIndex, 1)
    const u = messages.value.pop()
    if (u?.role === 'user') {
      input.value = question
    }
  } finally {
    sending.value = false
    await scrollToBottom()
  }
}

onMounted(async () => {
  await loadModules()
  await loadSessions()
})
</script>

<style scoped>
.chat-page {
  display: flex;
  height: calc(100vh - 24px);
  min-height: 420px;
  margin: -8px -12px 0;
  background: var(--el-bg-color-page);
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid var(--el-border-color-lighter);
}

.chat-sidebar {
  width: 240px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--el-border-color-lighter);
  background: var(--el-fill-color-blank);
  padding: 12px;
}

.new-chat-btn {
  width: 100%;
  margin-bottom: 12px;
}

.session-list {
  flex: 1;
  overflow-y: auto;
  min-height: 0;
}

.session-item {
  display: flex;
  align-items: center;
  padding: 10px 12px;
  margin-bottom: 6px;
  border-radius: 8px;
  cursor: pointer;
  font-size: 13px;
  color: var(--el-text-color-regular);
  border: 1px solid transparent;
  transition: background 0.15s, border-color 0.15s;
}

.session-delete-btn {
  flex-shrink: 0;
  margin-left: auto;
  font-size: 14px;
  color: var(--el-text-color-placeholder);
  opacity: 0;
  transition: opacity 0.15s, color 0.15s;
  cursor: pointer;
}

.session-item:hover .session-delete-btn {
  opacity: 1;
}

.session-delete-btn:hover {
  color: var(--el-color-danger);
}

.session-item:hover {
  background: var(--el-fill-color-light);
}

.session-item.active {
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary-light-5);
  color: var(--el-color-primary);
  font-weight: 500;
}

.session-title {
  flex: 1;
  min-width: 0;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  background: var(--el-bg-color);
}

.module-filter {
  flex-shrink: 0;
  padding: 12px 16px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  background: var(--el-fill-color-blank);
}

.module-filter-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
  text-transform: uppercase;
  letter-spacing: 0.04em;
  margin-bottom: 8px;
}

.module-checkboxes {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.module-row {
  display: inline-flex;
}

.type-tag {
  font-size: 11px;
  opacity: 0.75;
  margin-left: 4px;
}

.type-tag--task {
  color: var(--el-color-warning);
}

.chat-scroll {
  flex: 1;
  overflow-y: auto;
  padding: 16px 20px;
  min-height: 0;
}

.empty-chat {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 200px;
  color: var(--el-text-color-secondary);
}

.empty-title {
  margin: 0 0 8px;
  font-size: 18px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.empty-hint {
  margin: 0;
  font-size: 14px;
}

.typing-hint {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  padding: 8px 0;
}

.spin {
  animation: spin 0.9s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.chat-composer {
  flex-shrink: 0;
  padding: 12px 16px 16px;
  border-top: 1px solid var(--el-border-color-lighter);
  background: var(--el-fill-color-blank);
}

.composer-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 8px;
}
</style>
