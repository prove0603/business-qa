<template>
  <el-container class="app-layout">
    <el-aside width="220px" class="app-aside">
      <div class="logo">
        <el-icon size="24"><ChatDotSquare /></el-icon>
        <span>业务问答</span>
      </div>
      <el-menu
        :default-active="route.path"
        router
        background-color="#1d1e1f"
        text-color="#bfcbd9"
        active-text-color="#409eff"
      >
        <el-menu-item index="/dashboard">
          <el-icon><DataAnalysis /></el-icon>
          <span>仪表盘</span>
        </el-menu-item>
        <el-menu-item index="/chat">
          <el-icon><ChatDotSquare /></el-icon>
          <span>对话</span>
        </el-menu-item>
        <el-menu-item index="/modules">
          <el-icon><Grid /></el-icon>
          <span>模块</span>
        </el-menu-item>
        <el-menu-item index="/documents">
          <el-icon><Document /></el-icon>
          <span>文档</span>
        </el-menu-item>
        <el-menu-item index="/changes">
          <el-icon><Bell /></el-icon>
          <template #title>
            <span>变更</span>
            <el-badge v-if="pendingCount > 0" :value="pendingCount" class="nav-badge" />
          </template>
        </el-menu-item>
        <el-divider style="margin: 8px 16px; border-color: #333; width: auto;" />
        <el-menu-item index="/prompts">
          <el-icon><Setting /></el-icon>
          <span>提示词</span>
        </el-menu-item>
        <el-menu-item index="/guardrails">
          <el-icon><Lock /></el-icon>
          <span>护栏规则</span>
        </el-menu-item>
        <el-menu-item index="/ai-models">
          <el-icon><Cpu /></el-icon>
          <span>模型</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container direction="vertical" class="app-right">
      <div class="announcement-bar">
        <div class="announcement-track">
          <span class="announcement-text">
            📢 该项目在试运行阶段，如有想法或者建议，请联系：慕槐（庄杰）
          </span>
          <span class="announcement-text">
            📢 该项目在试运行阶段，如有想法或者建议，请联系：慕槐（庄杰）
          </span>
        </div>
      </div>
      <el-main class="app-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { Setting, Lock, Cpu } from '@element-plus/icons-vue'
import { dashboardApi } from './api'

const route = useRoute()
const pendingCount = ref(0)

onMounted(async () => {
  try {
    const data: any = await dashboardApi.getOverview()
    pendingCount.value = data.pendingSuggestionCount || 0
  } catch {
    // ignore
  }
})
</script>

<style scoped lang="scss">
.app-layout {
  height: 100vh;
}

.app-aside {
  background-color: #1d1e1f;
  overflow-y: auto;

  .logo {
    height: 60px;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 10px;
    color: #fff;
    font-size: 18px;
    font-weight: 600;
    border-bottom: 1px solid #333;
  }
}

.app-right {
  flex: 1;
  overflow: hidden;
}

.announcement-bar {
  background: linear-gradient(90deg, #e6f7ff, #fff7e6);
  overflow: hidden;
  white-space: nowrap;
  height: 32px;
  line-height: 32px;
  flex-shrink: 0;
  border-bottom: 1px solid #e4e7ed;
}

.announcement-track {
  display: inline-block;
  animation: marquee 28s linear infinite;
}

.announcement-text {
  display: inline-block;
  padding: 0 60px;
  font-size: 13px;
  color: #e6a23c;
  font-weight: 500;
}

@keyframes marquee {
  0%   { transform: translateX(0); }
  100% { transform: translateX(-50%); }
}

.app-main {
  background-color: #f5f7fa;
  overflow-y: auto;
}

.nav-badge {
  margin-left: 8px;
}
</style>
