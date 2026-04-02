<template>
  <div class="prompts-page">
    <div class="page-header">
      <h2>提示词管理</h2>
      <p class="page-desc">管理 AI 对话和分析的系统提示词，修改后需重启服务生效</p>
      <el-button type="primary" :icon="Plus" @click="openCreate">新建提示词</el-button>
    </div>

    <el-table :data="templates" v-loading="loading" stripe>
      <el-table-column prop="templateName" label="名称" width="200" />
      <el-table-column prop="templateKey" label="Key" width="180">
        <template #default="{ row }">
          <el-tag size="small" type="info">{{ row.templateKey }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="templateType" label="类型" width="100">
        <template #default="{ row }">
          <el-tag :type="row.templateType === 'SYSTEM' ? 'primary' : 'success'" size="small">
            {{ row.templateType === 'SYSTEM' ? '系统' : '用户' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="description" label="说明" show-overflow-tooltip />
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
      :title="editingId ? '编辑提示词' : '新建提示词'"
      width="720px"
      destroy-on-close
    >
      <el-form :model="form" label-width="100px" label-position="top">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="名称" required>
              <el-input v-model="form.templateName" placeholder="如：对话系统提示词" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="Key" required>
              <el-input v-model="form.templateKey" placeholder="如：CHAT_SYSTEM" :disabled="!!editingId" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="类型">
              <el-select v-model="form.templateType" style="width: 100%">
                <el-option label="系统提示词 (SYSTEM)" value="SYSTEM" />
                <el-option label="用户提示词模板 (USER)" value="USER" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="说明">
              <el-input v-model="form.description" placeholder="提示词用途说明" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="提示词内容" required>
          <div class="prompt-editor-wrapper">
            <div class="editor-toolbar">
              <span class="char-count">{{ form.content?.length || 0 }} 字符</span>
            </div>
            <el-input
              v-model="form.content"
              type="textarea"
              :rows="16"
              placeholder="输入提示词内容…&#10;支持 %s 占位符用于动态替换"
              class="prompt-textarea"
            />
          </div>
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
import { Plus } from '@element-plus/icons-vue'
import { promptApi } from '../api'

interface PromptRow {
  id: number
  templateName: string
  templateKey: string
  content: string
  templateType: string
  description?: string
  isActive: boolean
}

const templates = ref<PromptRow[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const saving = ref(false)
const editingId = ref<number | null>(null)

const form = reactive({
  templateName: '',
  templateKey: '',
  content: '',
  templateType: 'SYSTEM',
  description: '',
})

async function loadTemplates() {
  loading.value = true
  try {
    const data = (await promptApi.list()) as unknown as PromptRow[]
    templates.value = Array.isArray(data) ? data : []
  } catch {
    ElMessage.error('加载提示词失败')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  Object.assign(form, { templateName: '', templateKey: '', content: '', templateType: 'SYSTEM', description: '' })
  dialogVisible.value = true
}

function openEdit(row: PromptRow) {
  editingId.value = row.id
  Object.assign(form, {
    templateName: row.templateName,
    templateKey: row.templateKey,
    content: row.content,
    templateType: row.templateType,
    description: row.description || '',
  })
  dialogVisible.value = true
}

async function handleSave() {
  if (!form.templateName || !form.templateKey || !form.content) {
    ElMessage.warning('请填写必填项')
    return
  }
  saving.value = true
  try {
    if (editingId.value) {
      await promptApi.update(editingId.value, { ...form })
    } else {
      await promptApi.create({ ...form, isActive: true })
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    await loadTemplates()
  } catch {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

async function handleDelete(id: number) {
  try {
    await promptApi.delete(id)
    ElMessage.success('已删除')
    await loadTemplates()
  } catch {
    ElMessage.error('删除失败')
  }
}

async function toggleActive(row: PromptRow) {
  try {
    await promptApi.toggle(row.id)
    await loadTemplates()
  } catch {
    ElMessage.error('状态切换失败')
  }
}

onMounted(loadTemplates)
</script>

<style scoped>
.prompts-page {
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

.prompt-editor-wrapper {
  width: 100%;
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  overflow: hidden;
}

.editor-toolbar {
  display: flex;
  justify-content: flex-end;
  padding: 4px 12px;
  background: var(--el-fill-color-light);
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.char-count {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.prompt-textarea :deep(.el-textarea__inner) {
  border: none;
  border-radius: 0;
  font-family: 'Cascadia Code', 'Fira Code', 'Consolas', monospace;
  font-size: 13px;
  line-height: 1.6;
  resize: vertical;
}
</style>
