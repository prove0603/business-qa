<template>
  <div class="guardrails-page">
    <div class="page-header">
      <h2>护栏规则</h2>
      <p class="page-desc">管理 AI 对话的输入/输出安全规则，实时生效无需重启</p>
      <el-button type="primary" :icon="Plus" @click="openCreate">新建规则</el-button>
    </div>

    <!-- 测试区域 -->
    <el-card class="test-card" shadow="never">
      <template #header>
        <span class="test-title">规则测试</span>
      </template>
      <div class="test-row">
        <el-input v-model="testInput" placeholder="输入测试文本，检查是否会触发护栏规则…" clearable />
        <el-button type="warning" @click="runTest" :loading="testing">测试</el-button>
      </div>
      <div v-if="testResult" class="test-result" :class="{ blocked: testResult.blocked }">
        <el-icon v-if="testResult.blocked"><CircleCloseFilled /></el-icon>
        <el-icon v-else><CircleCheckFilled /></el-icon>
        <span>{{ testResult.blocked ? `已拦截 (${testResult.ruleName}): ${testResult.message}` : '通过，未触发任何规则' }}</span>
      </div>
    </el-card>

    <el-table :data="rules" v-loading="loading" stripe>
      <el-table-column prop="ruleName" label="规则名称" width="200" />
      <el-table-column prop="ruleType" label="类型" width="130">
        <template #default="{ row }">
          <el-tag :type="ruleTypeColor(row.ruleType)" size="small">{{ ruleTypeLabel(row.ruleType) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="action" label="动作" width="80">
        <template #default="{ row }">
          <el-tag :type="row.action === 'BLOCK' ? 'danger' : row.action === 'WARN' ? 'warning' : 'info'" size="small">
            {{ actionLabel(row.action) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="pattern" label="匹配模式" show-overflow-tooltip>
        <template #default="{ row }">
          <code class="pattern-code">{{ row.pattern }}</code>
        </template>
      </el-table-column>
      <el-table-column prop="description" label="说明" show-overflow-tooltip />
      <el-table-column prop="sortOrder" label="排序" width="60" align="center" />
      <el-table-column label="状态" width="80" align="center">
        <template #default="{ row }">
          <el-switch :model-value="row.isActive" @change="toggleActive(row)" size="small" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160" align="center">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-popconfirm title="确定删除？" @confirm="handleDelete(row.id)">
            <template #reference>
              <el-button link type="danger">删除</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '编辑规则' : '新建规则'"
      width="620px"
      destroy-on-close
    >
      <el-form :model="form" label-width="100px" label-position="top">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="规则名称" required>
              <el-input v-model="form.ruleName" placeholder="如：Prompt注入防护" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="排序权重">
              <el-input-number v-model="form.sortOrder" :min="0" :max="999" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="规则类型" required>
              <el-select v-model="form.ruleType" style="width: 100%">
                <el-option label="输入关键词匹配" value="INPUT_KEYWORD" />
                <el-option label="输入正则匹配" value="INPUT_REGEX" />
                <el-option label="输出正则脱敏" value="OUTPUT_REGEX" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="触发动作" required>
              <el-select v-model="form.action" style="width: 100%">
                <el-option label="拦截 (BLOCK)" value="BLOCK" />
                <el-option label="警告 (WARN)" value="WARN" />
                <el-option label="脱敏 (MASK)" value="MASK" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="匹配模式" required>
          <el-input
            v-model="form.pattern"
            type="textarea"
            :rows="3"
            :placeholder="form.ruleType === 'INPUT_KEYWORD' ? '多个关键词用逗号分隔' : '正则表达式'"
          />
        </el-form-item>
        <el-form-item label="拦截回复" v-if="form.action === 'BLOCK' || form.action === 'WARN'">
          <el-input v-model="form.replyMessage" placeholder="被拦截时返回给用户的提示信息" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="form.description" placeholder="规则用途说明" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave" :loading="saving">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, CircleCloseFilled, CircleCheckFilled } from '@element-plus/icons-vue'
import { guardrailApi } from '../api'

interface GuardrailRow {
  id: number
  ruleName: string
  ruleType: string
  pattern: string
  action: string
  replyMessage?: string
  description?: string
  sortOrder: number
  isActive: boolean
}

interface TestResultType {
  blocked: boolean
  action?: string
  message?: string
  ruleName?: string
}

const rules = ref<GuardrailRow[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const saving = ref(false)
const editingId = ref<number | null>(null)

const testInput = ref('')
const testing = ref(false)
const testResult = ref<TestResultType | null>(null)

const form = reactive({
  ruleName: '',
  ruleType: 'INPUT_REGEX',
  pattern: '',
  action: 'BLOCK',
  replyMessage: '',
  description: '',
  sortOrder: 0,
})

function ruleTypeLabel(t: string) {
  return { INPUT_KEYWORD: '输入关键词', INPUT_REGEX: '输入正则', OUTPUT_REGEX: '输出脱敏' }[t] || t
}

function ruleTypeColor(t: string) {
  return t.startsWith('INPUT') ? 'warning' : 'info'
}

function actionLabel(a: string) {
  return { BLOCK: '拦截', WARN: '警告', MASK: '脱敏' }[a] || a
}

async function loadRules() {
  loading.value = true
  try {
    const data = (await guardrailApi.list()) as unknown as GuardrailRow[]
    rules.value = Array.isArray(data) ? data : []
  } catch {
    ElMessage.error('加载护栏规则失败')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  Object.assign(form, { ruleName: '', ruleType: 'INPUT_REGEX', pattern: '', action: 'BLOCK', replyMessage: '', description: '', sortOrder: 0 })
  dialogVisible.value = true
}

function openEdit(row: GuardrailRow) {
  editingId.value = row.id
  Object.assign(form, {
    ruleName: row.ruleName,
    ruleType: row.ruleType,
    pattern: row.pattern,
    action: row.action,
    replyMessage: row.replyMessage || '',
    description: row.description || '',
    sortOrder: row.sortOrder,
  })
  dialogVisible.value = true
}

async function handleSave() {
  if (!form.ruleName || !form.pattern) {
    ElMessage.warning('请填写必填项')
    return
  }
  saving.value = true
  try {
    if (editingId.value) {
      await guardrailApi.update(editingId.value, { ...form })
    } else {
      await guardrailApi.create({ ...form, isActive: true })
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    await loadRules()
  } catch {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

async function handleDelete(id: number) {
  try {
    await guardrailApi.delete(id)
    ElMessage.success('已删除')
    await loadRules()
  } catch {
    ElMessage.error('删除失败')
  }
}

async function toggleActive(row: GuardrailRow) {
  try {
    await guardrailApi.toggle(row.id)
    await loadRules()
  } catch {
    ElMessage.error('状态切换失败')
  }
}

async function runTest() {
  if (!testInput.value.trim()) {
    ElMessage.warning('请输入测试文本')
    return
  }
  testing.value = true
  testResult.value = null
  try {
    const data = (await guardrailApi.test(testInput.value)) as unknown as TestResultType
    testResult.value = data
  } catch {
    ElMessage.error('测试失败')
  } finally {
    testing.value = false
  }
}

onMounted(loadRules)
</script>

<style scoped>
.guardrails-page {
  padding: 20px;
}

.page-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
}

.page-header h2 {
  margin: 0;
  font-size: 20px;
}

.page-desc {
  margin: 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  flex: 1;
}

.test-card {
  margin-bottom: 20px;
}

.test-title {
  font-weight: 600;
  font-size: 14px;
}

.test-row {
  display: flex;
  gap: 12px;
}

.test-result {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
  padding: 10px 14px;
  border-radius: 6px;
  font-size: 13px;
  background: var(--el-color-success-light-9);
  color: var(--el-color-success);
}

.test-result.blocked {
  background: var(--el-color-danger-light-9);
  color: var(--el-color-danger);
}

.pattern-code {
  font-family: 'Cascadia Code', 'Fira Code', monospace;
  font-size: 12px;
  background: var(--el-fill-color-light);
  padding: 2px 6px;
  border-radius: 3px;
}
</style>
