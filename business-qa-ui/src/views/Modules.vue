<template>
  <div class="modules-page">
    <div class="toolbar">
      <h1 class="title">模块管理</h1>
      <el-button type="primary" @click="openCreate">新增模块</el-button>
    </div>

    <el-table
      v-loading="loading"
      :data="list"
      stripe
      border
      style="width: 100%"
      empty-text="暂无模块"
    >
      <el-table-column prop="moduleName" label="模块名称" min-width="140" show-overflow-tooltip />
      <el-table-column prop="moduleCode" label="模块编码" min-width="120" show-overflow-tooltip />
      <el-table-column label="模块类型" width="110" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.moduleType === 'COMMON'" type="success">通用</el-tag>
          <el-tag v-else-if="row.moduleType === 'TASK'" type="primary">任务</el-tag>
          <el-tag v-else type="info">{{ row.moduleType }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="gitRemoteUrl" label="Git 地址" min-width="200" show-overflow-tooltip />
      <el-table-column prop="gitBranch" label="分支" width="120" show-overflow-tooltip />
      <el-table-column prop="description" label="描述" min-width="160" show-overflow-tooltip />
      <el-table-column label="操作" width="260" fixed="right" align="center">
        <template #default="{ row }">
          <el-button
            v-if="row.gitRemoteUrl"
            link
            type="primary"
            :loading="detectingId === row.id"
            @click="onDetect(row)"
          >
            检测变更
          </el-button>
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑模块' : '新增模块'"
      width="560px"
      destroy-on-close
      @closed="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
        <el-form-item label="模块名称" prop="moduleName">
          <el-input v-model="form.moduleName" placeholder="显示名称" clearable />
        </el-form-item>
        <el-form-item label="模块编码" prop="moduleCode">
          <el-input v-model="form.moduleCode" placeholder="唯一编码" clearable />
        </el-form-item>
        <el-form-item label="Git 地址" prop="gitRemoteUrl">
          <el-input v-model="form.gitRemoteUrl" placeholder="https://..." clearable />
        </el-form-item>
        <el-form-item label="分支" prop="gitBranch">
          <el-input v-model="form.gitBranch" placeholder="例如 main" clearable />
        </el-form-item>
        <el-form-item label="模块类型" prop="moduleType">
          <el-radio-group v-model="form.moduleType">
            <el-radio value="COMMON">通用</el-radio>
            <el-radio value="TASK">任务</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            placeholder="选填"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import { moduleApi, changeApi } from '../api'

interface ModuleItem {
  id: number
  moduleName: string
  moduleCode: string
  moduleType: 'COMMON' | 'TASK' | string
  gitRemoteUrl?: string
  gitBranch?: string
  description?: string
}

const loading = ref(false)
const saving = ref(false)
const list = ref<ModuleItem[]>([])
const dialogVisible = ref(false)
const isEdit = ref(false)
const editingId = ref<number | null>(null)
const detectingId = ref<number | null>(null)
const formRef = ref<FormInstance>()

const form = reactive({
  moduleName: '',
  moduleCode: '',
  gitRemoteUrl: '',
  gitBranch: '',
  moduleType: 'COMMON' as 'COMMON' | 'TASK',
  description: '',
})

const rules: FormRules = {
  moduleName: [{ required: true, message: '请输入模块名称', trigger: 'blur' }],
  moduleCode: [{ required: true, message: '请输入模块编码', trigger: 'blur' }],
  moduleType: [{ required: true, message: '请选择模块类型', trigger: 'change' }],
}

function resetForm() {
  form.moduleName = ''
  form.moduleCode = ''
  form.gitRemoteUrl = ''
  form.gitBranch = ''
  form.moduleType = 'COMMON'
  form.description = ''
  editingId.value = null
  isEdit.value = false
  formRef.value?.clearValidate()
}

function openCreate() {
  resetForm()
  isEdit.value = false
  dialogVisible.value = true
}

function openEdit(row: ModuleItem) {
  isEdit.value = true
  editingId.value = row.id
  form.moduleName = row.moduleName ?? ''
  form.moduleCode = row.moduleCode ?? ''
  form.gitRemoteUrl = row.gitRemoteUrl ?? ''
  form.gitBranch = row.gitBranch ?? ''
  form.moduleType = (row.moduleType === 'TASK' ? 'TASK' : 'COMMON') as 'COMMON' | 'TASK'
  form.description = row.description ?? ''
  dialogVisible.value = true
}

async function loadList() {
  loading.value = true
  try {
    const data = (await moduleApi.list()) as unknown
    list.value = Array.isArray(data) ? (data as ModuleItem[]) : []
  } catch {
    ElMessage.error('加载模块列表失败')
    list.value = []
  } finally {
    loading.value = false
  }
}

async function submitForm() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid: boolean) => {
    if (!valid) return
    saving.value = true
    try {
      const payload = {
        moduleName: form.moduleName,
        moduleCode: form.moduleCode,
        gitRemoteUrl: form.gitRemoteUrl || undefined,
        gitBranch: form.gitBranch || undefined,
        moduleType: form.moduleType,
        description: form.description || undefined,
      }
      if (isEdit.value && editingId.value != null) {
        await moduleApi.update(editingId.value, payload)
        ElMessage.success('已更新模块')
      } else {
        await moduleApi.create(payload)
        ElMessage.success('已创建模块')
      }
      dialogVisible.value = false
      await loadList()
    } catch {
      ElMessage.error(isEdit.value ? '更新模块失败' : '创建模块失败')
    } finally {
      saving.value = false
    }
  })
}

async function onDelete(row: ModuleItem) {
  try {
    await ElMessageBox.confirm(
      `确定删除模块「${row.moduleName}」吗？此操作不可撤销。`,
      '确定',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await moduleApi.delete(row.id)
    ElMessage.success('已删除模块')
    await loadList()
  } catch {
    ElMessage.error('删除模块失败')
  }
}

async function onDetect(row: ModuleItem) {
  detectingId.value = row.id
  try {
    await changeApi.detect(row.id)
    ElMessage.success('变更检测完成')
  } catch {
    ElMessage.error('变更检测失败')
  } finally {
    detectingId.value = null
  }
}

onMounted(() => {
  loadList()
})
</script>

<style scoped>
.modules-page {
  padding: 8px 0;
}

.toolbar {
  display: flex;
  align-items: center;
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
</style>
