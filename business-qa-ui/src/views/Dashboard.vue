<template>
  <div class="dashboard">
    <div class="dashboard-header">
      <h1 class="title">仪表盘</h1>
      <p v-if="lastRefreshedAt" class="meta">
        上次刷新：{{ formatTime(lastRefreshedAt) }}
      </p>
    </div>

    <el-row :gutter="20">
      <el-col :xs="24" :sm="12" :md="6">
        <el-card class="stat-card" shadow="hover">
          <div class="stat-value">{{ overview.moduleCount }}</div>
          <div class="stat-label">模块数</div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <el-card class="stat-card" shadow="hover">
          <div class="stat-value">{{ overview.documentCount }}</div>
          <div class="stat-label">文档数</div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <el-card class="stat-card" shadow="hover">
          <div class="stat-value">{{ overview.chatSessionCount }}</div>
          <div class="stat-label">对话数</div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <el-card class="stat-card" shadow="hover">
          <div class="stat-value">{{ overview.pendingSuggestionCount }}</div>
          <div class="stat-label">待处理建议</div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { dashboardApi } from '../api'
import { formatTime } from '../utils/format'

interface Overview {
  moduleCount: number
  documentCount: number
  chatSessionCount: number
  pendingSuggestionCount: number
}

const overview = ref<Overview>({
  moduleCount: 0,
  documentCount: 0,
  chatSessionCount: 0,
  pendingSuggestionCount: 0,
})

const lastRefreshedAt = ref<string>('')

async function loadOverview() {
  try {
    const data = (await dashboardApi.getOverview()) as unknown as Overview
    overview.value = {
      moduleCount: data.moduleCount ?? 0,
      documentCount: data.documentCount ?? 0,
      chatSessionCount: data.chatSessionCount ?? 0,
      pendingSuggestionCount: data.pendingSuggestionCount ?? 0,
    }
    lastRefreshedAt.value = new Date().toISOString()
  } catch {
    ElMessage.error('加载仪表盘概览失败')
  }
}

onMounted(() => {
  loadOverview()
})
</script>

<style scoped>
.dashboard {
  padding: 8px 0;
}

.dashboard-header {
  margin-bottom: 24px;
}

.title {
  margin: 0 0 8px;
  font-size: 22px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.meta {
  margin: 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.stat-card {
  margin-bottom: 20px;
  text-align: center;
  border-radius: 8px;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  line-height: 1.2;
  color: var(--el-color-primary);
  margin-bottom: 8px;
}

.stat-label {
  font-size: 14px;
  color: var(--el-text-color-secondary);
}
</style>
