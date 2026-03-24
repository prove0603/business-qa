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
      </el-menu>
    </el-aside>
    <el-main class="app-main">
      <router-view />
    </el-main>
  </el-container>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
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

.app-main {
  background-color: #f5f7fa;
  overflow-y: auto;
}

.nav-badge {
  margin-left: 8px;
}
</style>
