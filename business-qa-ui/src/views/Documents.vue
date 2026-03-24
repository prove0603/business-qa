<template>
  <div class="documents-page">
    <div class="toolbar">
      <h1 class="title">文档管理</h1>
      <div class="toolbar-actions">
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
        <el-button type="primary" @click="openCreate">新增文档</el-button>
        <el-button type="success" @click="openUpload">上传文件</el-button>
        <el-button :loading="vectorizeAllLoading" @click="onVectorizeAll">全部向量化</el-button>
      </div>
    </div>

    <el-table
      v-loading="loading"
      :data="rows"
      stripe
      border
      row-key="id"
      style="width: 100%"
      empty-text="暂无文档"
    >
      <el-table-column prop="title" label="标题" min-width="160" show-overflow-tooltip />
      <el-table-column label="模块" min-width="120" show-overflow-tooltip>
        <template #default="{ row }">
          {{ moduleLabel(row.moduleId) }}
        </template>
      </el-table-column>
      <el-table-column prop="fileType" label="文件类型" width="110" align="center" />
      <el-table-column label="来源" width="110" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.fileKey" type="info" size="small">文件</el-tag>
          <el-tag v-else type="" size="small">在线</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="分块数" width="90" align="center">
        <template #default="{ row }">
          {{ row.chunkCount ?? 0 }}
        </template>
      </el-table-column>
      <el-table-column label="已向量化" width="110" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.vectorized" type="success">是</el-tag>
          <el-tag v-else type="warning">待处理</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="version" label="版本" width="90" align="center" />
      <el-table-column label="更新时间" width="170">
        <template #default="{ row }">
          {{ formatTime(row.updateTime) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="260" fixed="right" align="center">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button
            v-if="row.fileKey"
            link
            type="primary"
            @click="onDownload(row)"
          >
            下载
          </el-button>
          <el-button
            link
            type="primary"
            :loading="vectorizingId === row.id"
            @click="onVectorize(row)"
          >
            向量化
          </el-button>
          <el-button link type="danger" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-wrap">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        background
        @size-change="loadDocuments"
        @current-change="loadDocuments"
      />
    </div>

    <!-- Online Edit Dialog -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑文档' : '新增文档'"
      width="720px"
      destroy-on-close
      @closed="resetForm"
    >
      <div v-loading="fetchDocLoading">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
        <el-form-item label="模块" prop="moduleId">
          <el-select
            v-model="form.moduleId"
            filterable
            placeholder="选择模块"
            style="width: 100%"
            :disabled="isEdit"
          >
            <el-option
              v-for="m in modules"
              :key="m.id"
              :label="m.moduleName"
              :value="m.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" placeholder="文档标题" clearable />
        </el-form-item>
        <el-form-item label="内容" prop="content">
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="15"
            placeholder="Markdown 或纯文本"
          />
        </el-form-item>
      </el-form>
      </div>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>

    <!-- File Upload Dialog -->
    <el-dialog
      v-model="uploadDialogVisible"
      title="上传文档文件"
      width="520px"
      destroy-on-close
      @closed="resetUploadForm"
    >
      <el-form ref="uploadFormRef" :model="uploadForm" :rules="uploadRules" label-width="120px">
        <el-form-item label="模块" prop="moduleId">
          <el-select
            v-model="uploadForm.moduleId"
            filterable
            placeholder="选择模块"
            style="width: 100%"
          >
            <el-option
              v-for="m in modules"
              :key="m.id"
              :label="m.moduleName"
              :value="m.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="标题">
          <el-input v-model="uploadForm.title" placeholder="留空则使用文件名" clearable />
        </el-form-item>
        <el-form-item label="文件" prop="file">
          <el-upload
            ref="uploadRef"
            :auto-upload="false"
            :limit="1"
            :on-change="onFileChange"
            :on-remove="onFileRemove"
            accept=".pdf,.doc,.docx,.txt,.md,.markdown,.rtf"
            drag
          >
            <el-icon class="el-icon--upload"><i class="el-icon-upload" /></el-icon>
            <div class="el-upload__text">
              将文件拖到此处，或<em>点击上传</em>
            </div>
            <template #tip>
              <div class="el-upload__tip">
                支持：PDF、Word、TXT、Markdown（最大 50MB）
              </div>
            </template>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="uploadDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="uploading" @click="submitUpload">上传</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import type { FormInstance, FormRules, UploadFile } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import { documentApi, moduleApi } from '../api'
import { formatTime } from '../utils/format'

interface ModuleItem {
  id: number
  moduleName: string
}

interface DocRow {
  id: number
  moduleId: number
  title: string
  fileType?: string
  fileKey?: string
  originalFilename?: string
  chunkCount?: number
  vectorized?: boolean
  version?: number
  updateTime?: string
}

interface PageResult<T> {
  total: number
  current: number
  size: number
  records: T[]
}

const loading = ref(false)
const fetchDocLoading = ref(false)
const saving = ref(false)
const uploading = ref(false)
const vectorizeAllLoading = ref(false)
const vectorizingId = ref<number | null>(null)
const modules = ref<ModuleItem[]>([])
const rows = ref<DocRow[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)
const filterModuleId = ref<number | undefined>(undefined)

// Online edit dialog
const dialogVisible = ref(false)
const isEdit = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()

const form = reactive({
  moduleId: undefined as number | undefined,
  title: '',
  content: '',
})

const rules: FormRules = {
  moduleId: [{ required: true, message: '请选择模块', trigger: 'change' }],
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  content: [{ required: true, message: '请输入内容', trigger: 'blur' }],
}

// Upload dialog
const uploadDialogVisible = ref(false)
const uploadFormRef = ref<FormInstance>()
const uploadRef = ref()
const selectedFile = ref<File | null>(null)

const uploadForm = reactive({
  moduleId: undefined as number | undefined,
  title: '',
  file: null as any,
})

const uploadRules: FormRules = {
  moduleId: [{ required: true, message: '请选择模块', trigger: 'change' }],
  file: [{ required: true, message: '请选择文件', trigger: 'change' }],
}

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

function resetForm() {
  form.moduleId = undefined
  form.title = ''
  form.content = ''
  editingId.value = null
  isEdit.value = false
  formRef.value?.clearValidate()
}

function resetUploadForm() {
  uploadForm.moduleId = undefined
  uploadForm.title = ''
  uploadForm.file = null
  selectedFile.value = null
  uploadFormRef.value?.clearValidate()
}

function openCreate() {
  resetForm()
  isEdit.value = false
  dialogVisible.value = true
}

function openUpload() {
  resetUploadForm()
  uploadDialogVisible.value = true
}

function onFileChange(file: UploadFile) {
  selectedFile.value = file.raw ?? null
  uploadForm.file = file.raw ?? null
  uploadFormRef.value?.validateField('file')
}

function onFileRemove() {
  selectedFile.value = null
  uploadForm.file = null
}

async function openEdit(row: DocRow) {
  isEdit.value = true
  editingId.value = row.id
  dialogVisible.value = true
  fetchDocLoading.value = true
  try {
    const doc = (await documentApi.get(row.id)) as unknown as DocRow & { content?: string }
    form.moduleId = doc.moduleId
    form.title = doc.title ?? ''
    form.content = doc.content ?? ''
  } catch {
    ElMessage.error('加载文档失败')
    dialogVisible.value = false
  } finally {
    fetchDocLoading.value = false
  }
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

async function loadDocuments() {
  loading.value = true
  try {
    const params: Record<string, number> = {
      current: currentPage.value,
      size: pageSize.value,
    }
    if (filterModuleId.value != null) {
      params.moduleId = filterModuleId.value
    }
    const res = (await documentApi.page(params)) as unknown as PageResult<DocRow>
    rows.value = res.records ?? []
    total.value = Number(res.total ?? 0)
  } catch {
    ElMessage.error('加载文档列表失败')
    rows.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function onFilterChange() {
  currentPage.value = 1
  loadDocuments()
}

async function submitForm() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid: boolean) => {
    if (!valid) return
    if (form.moduleId == null) return
    saving.value = true
    try {
      if (isEdit.value && editingId.value != null) {
        await documentApi.update(editingId.value, {
          title: form.title,
          content: form.content,
        })
        ElMessage.success('文档已更新')
      } else {
        await documentApi.create({
          moduleId: form.moduleId,
          title: form.title,
          content: form.content,
        })
        ElMessage.success('文档已创建')
      }
      dialogVisible.value = false
      await loadDocuments()
    } catch {
      ElMessage.error(isEdit.value ? '更新文档失败' : '创建文档失败')
    } finally {
      saving.value = false
    }
  })
}

async function submitUpload() {
  if (!uploadFormRef.value) return
  await uploadFormRef.value.validate(async (valid: boolean) => {
    if (!valid) return
    if (!selectedFile.value || uploadForm.moduleId == null) return
    uploading.value = true
    try {
      const formData = new FormData()
      formData.append('file', selectedFile.value)
      formData.append('moduleId', String(uploadForm.moduleId))
      if (uploadForm.title) {
        formData.append('title', uploadForm.title)
      }
      await documentApi.upload(formData)
      ElMessage.success('文件已上传并解析成功')
      uploadDialogVisible.value = false
      await loadDocuments()
    } catch (e: any) {
      const msg = e?.response?.data?.message || '上传失败'
      ElMessage.error(msg)
    } finally {
      uploading.value = false
    }
  })
}

async function onDownload(row: DocRow) {
  try {
    const res = await documentApi.download(row.id) as any
    const blob = new Blob([res])
    const url = window.URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = row.originalFilename || row.title || '文档'
    a.click()
    window.URL.revokeObjectURL(url)
  } catch {
    ElMessage.error('下载失败')
  }
}

async function onDelete(row: DocRow) {
  try {
    await ElMessageBox.confirm(
      `确定删除文档「${row.title}」？此操作不可撤销。`,
      '确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await documentApi.delete(row.id)
    ElMessage.success('文档已删除')
    await loadDocuments()
  } catch {
    ElMessage.error('删除文档失败')
  }
}

async function onVectorize(row: DocRow) {
  vectorizingId.value = row.id
  try {
    await documentApi.vectorize(row.id)
    ElMessage.success('向量化任务已加入队列')
    await loadDocuments()
  } catch {
    ElMessage.error('向量化任务排队失败')
  } finally {
    vectorizingId.value = null
  }
}

async function onVectorizeAll() {
  try {
    await ElMessageBox.confirm(
      '是否重新向量化全部文档？可能需要较长时间。',
      '确认',
      { type: 'warning', confirmButtonText: '开始', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  vectorizeAllLoading.value = true
  try {
    await documentApi.vectorizeAll()
    ElMessage.success('全部向量化任务已启动')
    await loadDocuments()
  } catch {
    ElMessage.error('启动全部向量化失败')
  } finally {
    vectorizeAllLoading.value = false
  }
}

onMounted(async () => {
  await loadModules()
  await loadDocuments()
})
</script>

<style scoped lang="scss">
.documents-page {
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

.toolbar-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.module-filter {
  min-width: 200px;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
