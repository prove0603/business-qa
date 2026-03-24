<template>
  <div class="changes-page">
    <div class="toolbar">
      <h1 class="title">变更通知</h1>
      <div class="toolbar-right">
        <el-select
          v-model="filterModuleId"
          clearable
          filterable
          placeholder="全部模块"
          class="module-filter"
          @change="onFilterChange"
        >
          <el-option
            v-for="m in modules"
            :key="m.id"
            :label="m.moduleName"
            :value="m.id"
          />
        </el-select>
        <el-alert
          class="pending-alert"
          :title="`待处理建议：${pendingCount}`"
          type="info"
          :closable="false"
          show-icon
        />
      </div>
    </div>

    <h2 class="section-title">检测历史</h2>
    <el-table
      v-loading="detectionsLoading"
      :data="detections"
      stripe
      border
      row-key="id"
      highlight-current-row
      style="width: 100%"
      empty-text="暂无检测记录"
      @row-click="onDetectionRowClick"
    >
      <el-table-column label="模块" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">
          {{ moduleLabel(row.moduleId) }}
        </template>
      </el-table-column>
      <el-table-column label="起始提交" width="120" align="center">
        <template #default="{ row }">
          <code class="commit">{{ shortHash(row.fromCommit) }}</code>
        </template>
      </el-table-column>
      <el-table-column label="目标提交" width="120" align="center">
        <template #default="{ row }">
          <code class="commit">{{ shortHash(row.toCommit) }}</code>
        </template>
      </el-table-column>
      <el-table-column label="变更文件数" width="150" align="center">
        <template #default="{ row }">
          {{ row.changedFileCount ?? 0 }}
        </template>
      </el-table-column>
      <el-table-column label="建议" width="110" align="center">
        <template #default="{ row }">
          {{ row.suggestionCount ?? 0 }}
        </template>
      </el-table-column>
      <el-table-column label="状态" width="120" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.status === 'COMPLETED'" type="success">{{ formatDetectionStatus(row.status) }}</el-tag>
          <el-tag v-else-if="row.status === 'FAILED'" type="danger">{{ formatDetectionStatus(row.status) }}</el-tag>
          <el-tag v-else-if="row.status === 'RUNNING'" type="warning">{{ formatDetectionStatus(row.status) }}</el-tag>
          <el-tag v-else type="info">{{ formatDetectionStatus(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="时间" width="170">
        <template #default="{ row }">
          {{ formatTime(row.createTime) }}
        </template>
      </el-table-column>
    </el-table>

    <h2 class="section-title">建议</h2>
    <div v-if="!selectedDetection" class="empty-hint">
      请在上方选择一条检测记录以查看建议。
    </div>
    <div v-else v-loading="suggestionsLoading" class="suggestion-list">
      <el-card
        v-for="s in suggestions"
        :key="s.id"
        class="suggestion-card"
        shadow="hover"
      >
        <div class="card-head">
          <span class="affected-title">{{ s.affectedSection || '—' }}</span>
          <el-tag
            :type="suggestionStatusTagType(s.status)"
            size="small"
          >
            {{ formatSuggestionStatus(s.status) }}
          </el-tag>
        </div>
        <div class="diff-block">
          <div class="diff-label">原文</div>
          <div class="original-text">{{ s.originalText || '—' }}</div>
          <div class="diff-label">建议修改</div>
          <div class="suggested-text">{{ s.suggestedText || '—' }}</div>
        </div>
        <div v-if="s.reason" class="reason">
          <span class="reason-label">原因：</span>
          {{ s.reason }}
        </div>
        <div class="card-actions">
          <el-button
            type="success"
            size="small"
            :loading="actionId === s.id && actionType === 'apply'"
            :disabled="!isPending(s.status)"
            @click="onApply(s)"
          >
            应用
          </el-button>
          <el-button
            size="small"
            :loading="actionId === s.id && actionType === 'ignore'"
            :disabled="!isPending(s.status)"
            @click="onIgnore(s)"
          >
            忽略
          </el-button>
        </div>
      </el-card>
      <div v-if="!suggestionsLoading && selectedDetection && suggestions.length === 0" class="empty-hint">
        本次检测暂无建议。
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { changeApi, moduleApi } from '../api'
import { formatTime } from '../utils/format'

interface ModuleItem {
  id: number
  moduleName: string
}

interface DetectionRow {
  id: number
  moduleId: number
  fromCommit?: string
  toCommit?: string
  changedFileCount?: number
  suggestionCount?: number
  status?: string
  createTime?: string
}

interface SuggestionRow {
  id: number
  detectionId?: number
  affectedSection?: string
  originalText?: string
  suggestedText?: string
  reason?: string
  status?: string
}

const modules = ref<ModuleItem[]>([])
const filterModuleId = ref<number | undefined>(undefined)
const pendingCount = ref(0)

const detectionsLoading = ref(false)
const detections = ref<DetectionRow[]>([])

const selectedDetection = ref<DetectionRow | null>(null)
const suggestionsLoading = ref(false)
const suggestions = ref<SuggestionRow[]>([])

const actionId = ref<number | null>(null)
const actionType = ref<'apply' | 'ignore' | null>(null)

const moduleNameMap = computed(() => {
  const m = new Map<number, string>()
  for (const mod of modules.value) {
    m.set(mod.id, mod.moduleName)
  }
  return m
})

function moduleLabel(moduleId: number) {
  return moduleNameMap.value.get(moduleId) ?? '—'
}

function shortHash(hash: string | null | undefined) {
  if (!hash) return '—'
  return hash.length > 8 ? hash.slice(0, 8) : hash
}

function suggestionStatusTagType(status: string | undefined) {
  if (status === 'APPLIED') return 'success' as const
  if (status === 'IGNORED') return 'info' as const
  return 'warning' as const
}

function isPending(status: string | undefined) {
  return !status || status === 'PENDING'
}

function formatDetectionStatus(status: string | undefined) {
  if (!status) return '—'
  if (status === 'COMPLETED') return '已完成'
  if (status === 'FAILED') return '失败'
  if (status === 'RUNNING') return '运行中'
  return status
}

function formatSuggestionStatus(status: string | undefined) {
  if (!status) return '待处理'
  if (status === 'PENDING') return '待处理'
  if (status === 'APPLIED') return '已应用'
  if (status === 'IGNORED') return '已忽略'
  return status
}

async function loadModules() {
  try {
    const data = (await moduleApi.list()) as unknown
    modules.value = Array.isArray(data) ? (data as ModuleItem[]) : []
  } catch {
    ElMessage.error('加载模块失败')
    modules.value = []
  }
}

async function loadPendingCount() {
  try {
    const data = (await changeApi.pendingSuggestions()) as unknown
    pendingCount.value = Array.isArray(data) ? data.length : 0
  } catch {
    pendingCount.value = 0
  }
}

async function loadDetections() {
  detectionsLoading.value = true
  try {
    const data = (await changeApi.listDetections(
      filterModuleId.value
    )) as unknown
    detections.value = Array.isArray(data) ? (data as DetectionRow[]) : []
  } catch {
    ElMessage.error('加载检测记录失败')
    detections.value = []
  } finally {
    detectionsLoading.value = false
  }
}

function onFilterChange() {
  selectedDetection.value = null
  suggestions.value = []
  loadDetections()
}

async function onDetectionRowClick(row: DetectionRow) {
  selectedDetection.value = row
  await loadSuggestions(row.id)
}

async function loadSuggestions(detectionId: number) {
  suggestionsLoading.value = true
  try {
    const data = (await changeApi.getSuggestions(detectionId)) as unknown
    suggestions.value = Array.isArray(data) ? (data as SuggestionRow[]) : []
  } catch {
    ElMessage.error('加载建议失败')
    suggestions.value = []
  } finally {
    suggestionsLoading.value = false
  }
}

async function onApply(s: SuggestionRow) {
  actionId.value = s.id
  actionType.value = 'apply'
  try {
    await changeApi.applySuggestion(s.id)
    ElMessage.success('已应用建议')
    await loadSuggestions(selectedDetection.value!.id)
    await loadPendingCount()
  } catch {
    ElMessage.error('应用建议失败')
  } finally {
    actionId.value = null
    actionType.value = null
  }
}

async function onIgnore(s: SuggestionRow) {
  actionId.value = s.id
  actionType.value = 'ignore'
  try {
    await changeApi.ignoreSuggestion(s.id)
    ElMessage.success('已忽略建议')
    await loadSuggestions(selectedDetection.value!.id)
    await loadPendingCount()
  } catch {
    ElMessage.error('忽略建议失败')
  } finally {
    actionId.value = null
    actionType.value = null
  }
}

onMounted(async () => {
  await loadModules()
  await loadPendingCount()
  await loadDetections()
})
</script>

<style scoped lang="scss">
.changes-page {
  padding: 8px 0;
}

.toolbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 20px;
  flex-wrap: wrap;
  gap: 12px;
}

.title {
  margin: 0;
  font-size: 22px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.module-filter {
  min-width: 200px;
}

.pending-alert {
  max-width: 280px;
  padding: 6px 12px;
}

.section-title {
  margin: 24px 0 12px;
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);

  &:first-of-type {
    margin-top: 0;
  }
}

.commit {
  font-size: 12px;
}

.empty-hint {
  color: var(--el-text-color-secondary);
  font-size: 14px;
  padding: 16px 0;
}

.suggestion-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.suggestion-card {
  :deep(.el-card__body) {
    padding: 16px;
  }
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.affected-title {
  font-weight: 600;
  font-size: 15px;
  color: var(--el-text-color-primary);
}

.diff-block {
  margin-bottom: 12px;
}

.diff-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 8px;
  margin-bottom: 4px;

  &:first-child {
    margin-top: 0;
  }
}

.original-text {
  white-space: pre-wrap;
  word-break: break-word;
  text-decoration: line-through;
  color: var(--el-color-danger);
  background: var(--el-fill-color-light);
  padding: 8px 10px;
  border-radius: 4px;
  font-size: 13px;
}

.suggested-text {
  white-space: pre-wrap;
  word-break: break-word;
  color: var(--el-color-success);
  background: var(--el-fill-color-light);
  padding: 8px 10px;
  border-radius: 4px;
  font-size: 13px;
}

.reason {
  font-size: 13px;
  color: var(--el-text-color-regular);
  margin-bottom: 12px;
  line-height: 1.5;
}

.reason-label {
  font-weight: 600;
  margin-right: 4px;
}

.card-actions {
  display: flex;
  gap: 8px;
}
</style>
