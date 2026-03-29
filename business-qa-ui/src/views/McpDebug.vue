<template>
  <div class="mcp-debug-page">
    <div class="header">
      <h1 class="title">MCP 调试</h1>
      <p class="subtitle">查看 MCP Client 状态，并直接通过现有问答链路验证工具调用。</p>
    </div>

    <el-row :gutter="16" class="status-row">
      <el-col :xs="24" :md="16">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>MCP Client 状态</span>
              <el-button size="small" :loading="statusLoading" @click="loadStatus">刷新状态</el-button>
            </div>
          </template>
          <div class="status-grid">
            <div class="status-item">
              <div class="label">Client 开关</div>
              <el-tag :type="status.clientEnabled ? 'success' : 'warning'">
                {{ status.clientEnabled ? '已启用' : '未启用' }}
              </el-tag>
            </div>
            <div class="status-item">
              <div class="label">发现工具数</div>
              <div class="value">{{ status.toolCount }}</div>
            </div>
          </div>
          <el-alert
            v-if="!status.clientEnabled"
            type="warning"
            :closable="false"
            show-icon
            title="MCP Client 未启用。请确认 QA_MCP_CLIENT_ENABLED=true，且 QA_MCP_SERVER_URL 可访问。"
          />
        </el-card>
      </el-col>
      <el-col :xs="24" :md="8">
        <el-card shadow="never" class="tips-card">
          <template #header>
            <span>推荐测试问题</span>
          </template>
          <div class="quick-prompts">
            <el-tag
              v-for="p in quickPrompts"
              :key="p"
              class="quick-tag"
              effect="plain"
              @click="question = p"
            >
              {{ p }}
            </el-tag>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never">
      <template #header>
        <span>流式联调</span>
      </template>

      <el-form label-position="top">
        <el-form-item label="问题">
          <el-input
            v-model="question"
            type="textarea"
            :rows="4"
            placeholder="输入测试问题，建议明确要求“调用 MCP 工具”"
          />
        </el-form-item>

        <el-form-item label="模块过滤（可选）">
          <el-select
            v-model="selectedModuleIds"
            multiple
            filterable
            clearable
            collapse-tags
            collapse-tags-tooltip
            placeholder="不选则仅使用通用模块"
            style="width: 100%;"
          >
            <el-option
              v-for="m in modules"
              :key="m.id"
              :label="`${m.moduleName || m.moduleCode} (${m.moduleType || '-'})`"
              :value="m.id"
            />
          </el-select>
        </el-form-item>
      </el-form>

      <div class="actions">
        <el-button type="primary" :loading="testing" :disabled="!question.trim()" @click="runStreamTest">
          开始测试
        </el-button>
        <el-button :disabled="testing" @click="clearResult">清空结果</el-button>
      </div>

      <div class="result-meta">
        <span>分片数：{{ chunkCount }}</span>
        <span v-if="lastError" class="error-text">错误：{{ lastError }}</span>
      </div>

      <el-input
        :model-value="answer"
        type="textarea"
        :rows="12"
        readonly
        placeholder="这里会显示 /api/chat/stream 的返回内容"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { mcpApi, moduleApi } from '../api'

interface McpStatus {
  clientEnabled: boolean
  toolCount: number
}

interface ModuleItem {
  id: number
  moduleName?: string
  moduleCode?: string
  moduleType?: string
}

const status = ref<McpStatus>({
  clientEnabled: false,
  toolCount: 0,
})
const statusLoading = ref(false)

const modules = ref<ModuleItem[]>([])
const selectedModuleIds = ref<number[]>([])

const question = ref('请优先调用 MCP 工具 currentServerTime，告诉我当前时间，并补充一句对 MCP 的理解。')
const answer = ref('')
const chunkCount = ref(0)
const testing = ref(false)
const lastError = ref('')

const quickPrompts = [
  '请调用 MCP 工具 currentServerTime，返回当前时间。',
  '请调用 MCP 工具 add，计算 12.5 + 7.3，并解释结果。',
  '请调用 MCP 工具 searchKnowledge，检索 rag，并简要总结。',
]

async function loadStatus() {
  statusLoading.value = true
  try {
    const data = await mcpApi.status()
    status.value = {
      clientEnabled: Boolean(data?.clientEnabled),
      toolCount: Number(data?.toolCount ?? 0),
    }
  } catch {
    ElMessage.error('加载 MCP 状态失败')
  } finally {
    statusLoading.value = false
  }
}

async function loadModules() {
  try {
    const data = await moduleApi.list()
    modules.value = Array.isArray(data) ? (data as ModuleItem[]) : []
  } catch {
    modules.value = []
  }
}

async function readSseTextStream(
  reader: ReadableStreamDefaultReader<Uint8Array>,
  onChunk: (text: string) => void
) {
  const decoder = new TextDecoder()
  let buffer = ''
  while (true) {
    const { done, value } = await reader.read()
    if (value) {
      buffer += decoder.decode(value, { stream: true })
    }

    let sep = -1
    while ((sep = buffer.indexOf('\n\n')) !== -1) {
      const block = buffer.slice(0, sep)
      buffer = buffer.slice(sep + 2)
      const lines = block.split('\n').filter(Boolean)
      const dataLines = lines.filter((line) => line.startsWith('data:'))
      if (dataLines.length > 0) {
        const text = dataLines.map((line) => line.slice(5).trimStart()).join('\n')
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

async function runStreamTest() {
  const q = question.value.trim()
  if (!q || testing.value) return

  testing.value = true
  answer.value = ''
  chunkCount.value = 0
  lastError.value = ''

  try {
    const response = await fetch('/api/chat/stream', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        question: q,
        moduleIds: selectedModuleIds.value,
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

    await readSseTextStream(body.getReader(), (text) => {
      answer.value += text
      chunkCount.value += 1
    })

    await loadStatus()
  } catch (e) {
    const msg = e instanceof Error ? e.message : '流式测试失败'
    lastError.value = msg
    ElMessage.error(msg)
  } finally {
    testing.value = false
  }
}

function clearResult() {
  answer.value = ''
  chunkCount.value = 0
  lastError.value = ''
}

onMounted(async () => {
  await Promise.all([loadStatus(), loadModules()])
})
</script>

<style scoped>
.mcp-debug-page {
  padding: 8px 0;
}

.header {
  margin-bottom: 16px;
}

.title {
  margin: 0 0 8px;
  font-size: 22px;
  font-weight: 600;
}

.subtitle {
  margin: 0;
  color: var(--el-text-color-secondary);
}

.status-row {
  margin-bottom: 16px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.status-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 12px;
}

.status-item .label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-bottom: 6px;
}

.status-item .value {
  font-size: 24px;
  font-weight: 700;
  color: var(--el-color-primary);
}

.tips-card {
  height: 100%;
}

.quick-prompts {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.quick-tag {
  cursor: pointer;
}

.actions {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}

.result-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.error-text {
  color: var(--el-color-danger);
}
</style>
