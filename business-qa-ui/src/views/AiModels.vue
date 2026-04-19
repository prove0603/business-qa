<template>
  <div class="ai-models">
    <h1 class="page-title">模型管理</h1>

    <el-dialog
      v-model="showNotice"
      title="⚠️ 注意"
      width="420px"
      :close-on-click-modal="false"
      :close-on-press-escape="false"
      :show-close="false"
      align-center
    >
      <p style="font-size: 15px; line-height: 1.8; margin: 0;">
        请勿随意修改模型配置，该页面为<b>慕槐（庄杰）</b>调试所用。<br />
        如需修改请联系<b>慕槐（庄杰）</b>。
      </p>
      <template #footer>
        <el-button type="primary" @click="showNotice = false">我已知晓</el-button>
      </template>
    </el-dialog>

    <!-- 当前配置 -->
    <el-card shadow="hover" class="section-card">
      <template #header>
        <span class="card-title">当前模型配置</span>
        <el-button text type="primary" :loading="configLoading" @click="loadConfig" style="float: right;">
          刷新
        </el-button>
      </template>
      <el-descriptions :column="1" border>
        <el-descriptions-item label="Chat 模型（当前生效）">
          <el-tag type="primary" size="large">{{ config.activeChatModel || '-' }}</el-tag>
          <el-tag v-if="config.activeChatModel !== config.defaultChatModel" type="warning" size="small" style="margin-left: 8px;">
            已切换（默认：{{ config.defaultChatModel }}）
          </el-tag>
          <el-button
            v-if="config.activeChatModel !== config.defaultChatModel"
            text type="danger" size="small" style="margin-left: 8px;"
            :loading="resetLoading"
            @click="handleReset"
          >
            恢复默认
          </el-button>
        </el-descriptions-item>
        <el-descriptions-item label="Embedding 模型">
          <el-tag type="success" size="large">{{ config.embeddingModel || '-' }}</el-tag>
          <el-text type="info" size="small" style="margin-left: 8px;">
            （仅文档向量化时使用，不支持运行时切换）
          </el-text>
        </el-descriptions-item>
      </el-descriptions>
    </el-card>

    <!-- 测试 & 切换 -->
    <el-card shadow="hover" class="section-card">
      <template #header>
        <span class="card-title">测试 & 切换模型</span>
      </template>

      <el-form :inline="true" @submit.prevent>
        <el-form-item label="模型名称">
          <el-input
            v-model="testModelName"
            placeholder="例如：qwen-turbo、qwen-plus-2025-04-28"
            style="width: 380px;"
            clearable
            @keyup.enter="handleTest"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="testLoading" @click="handleTest" :disabled="!testModelName.trim()">
            测试连通性
          </el-button>
          <el-button
            type="success"
            :loading="switchLoading"
            @click="handleSwitch"
            :disabled="!testModelName.trim()"
          >
            切换为此模型
          </el-button>
        </el-form-item>
      </el-form>

      <!-- 测试结果 -->
      <el-result
        v-if="testResult"
        :icon="testResult.success ? 'success' : 'error'"
        :title="testResult.success ? '连通性测试成功' : '连通性测试失败'"
        :sub-title="testResultSummary"
        style="margin-top: 16px;"
      >
        <template #extra>
          <el-descriptions v-if="testResult" :column="1" border size="small" style="max-width: 500px;">
            <el-descriptions-item label="模型">{{ testResultModel }}</el-descriptions-item>
            <el-descriptions-item label="耗时">{{ testResult.elapsedMs }} ms</el-descriptions-item>
            <el-descriptions-item v-if="testResult.response" label="响应内容">{{ testResult.response }}</el-descriptions-item>
            <el-descriptions-item v-if="testResult.error" label="错误信息">
              <el-text type="danger">{{ testResult.error }}</el-text>
            </el-descriptions-item>
          </el-descriptions>
        </template>
      </el-result>
    </el-card>

    <!-- 常用模型速查 -->
    <el-card shadow="hover" class="section-card">
      <template #header>
        <span class="card-title">百炼（DashScope）常用模型</span>
      </template>
      <el-table :data="commonModels" stripe style="width: 100%;" size="small">
        <el-table-column prop="name" label="模型名称" width="280" />
        <el-table-column prop="desc" label="说明" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" size="small" @click="fillModel(row.name)">填入</el-button>
            <el-button text type="success" size="small" @click="quickTest(row.name)">快速测试</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { modelApi } from '../api'

interface ModelConfig {
  activeChatModel: string
  defaultChatModel: string
  embeddingModel: string
}

interface TestResultData {
  success: boolean
  response: string | null
  elapsedMs: number
  error: string | null
}

const showNotice = ref(true)
const config = ref<ModelConfig>({ activeChatModel: '', defaultChatModel: '', embeddingModel: '' })
const configLoading = ref(false)
const testModelName = ref('')
const testLoading = ref(false)
const switchLoading = ref(false)
const resetLoading = ref(false)
const testResult = ref<TestResultData | null>(null)
const testResultModel = ref('')

const testResultSummary = computed(() => {
  if (!testResult.value) return ''
  if (testResult.value.success) {
    return `模型 ${testResultModel.value} 响应正常，耗时 ${testResult.value.elapsedMs}ms`
  }
  return `模型 ${testResultModel.value} 调用失败`
})

const commonModels = [
  { name: 'qwen-turbo', desc: '速度最快，成本最低，适合简单任务' },
  { name: 'qwen-plus', desc: '能力均衡，适合大多数场景' },
  { name: 'qwen-max', desc: '最强能力，适合复杂推理' },
  { name: 'qwen3.6-plus', desc: '当前默认模型' },
  { name: 'qwen-long', desc: '长文本处理，支持超长上下文' },
]

async function loadConfig() {
  configLoading.value = true
  try {
    const data = await modelApi.getConfig() as unknown as ModelConfig
    config.value = data
  } catch {
    ElMessage.error('加载模型配置失败')
  } finally {
    configLoading.value = false
  }
}

async function handleTest() {
  const name = testModelName.value.trim()
  if (!name) return
  testLoading.value = true
  testResult.value = null
  testResultModel.value = name
  try {
    const data = await modelApi.test(name) as unknown as TestResultData
    testResult.value = data
    if (data.success) {
      ElMessage.success(`模型 ${name} 测试通过（${data.elapsedMs}ms）`)
    } else {
      ElMessage.error(`模型 ${name} 测试失败`)
    }
  } catch (e: any) {
    ElMessage.error('测试请求失败：' + (e.message || '未知错误'))
  } finally {
    testLoading.value = false
  }
}

async function handleSwitch() {
  const name = testModelName.value.trim()
  if (!name) return
  try {
    await ElMessageBox.confirm(
      `确定要将 Chat 模型切换为 "${name}" 吗？切换后所有新对话都将使用此模型。`,
      '切换模型确认',
      { type: 'warning' }
    )
  } catch {
    return
  }
  switchLoading.value = true
  try {
    await modelApi.switchModel(name)
    ElMessage.success(`已切换为 ${name}`)
    await loadConfig()
  } catch (e: any) {
    ElMessage.error('切换失败：' + (e.message || '未知错误'))
  } finally {
    switchLoading.value = false
  }
}

async function handleReset() {
  try {
    await ElMessageBox.confirm(
      `确定要恢复为默认模型 "${config.value.defaultChatModel}" 吗？`,
      '恢复默认确认',
      { type: 'info' }
    )
  } catch {
    return
  }
  resetLoading.value = true
  try {
    await modelApi.reset()
    ElMessage.success('已恢复为默认模型')
    await loadConfig()
  } catch (e: any) {
    ElMessage.error('恢复失败：' + (e.message || '未知错误'))
  } finally {
    resetLoading.value = false
  }
}

function fillModel(name: string) {
  testModelName.value = name
}

function quickTest(name: string) {
  testModelName.value = name
  handleTest()
}

onMounted(() => {
  loadConfig()
})
</script>

<style scoped>
.ai-models {
  padding: 8px 0;
}

.page-title {
  margin: 0 0 20px;
  font-size: 22px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.section-card {
  margin-bottom: 20px;
}

.card-title {
  font-weight: 600;
  font-size: 15px;
}
</style>
