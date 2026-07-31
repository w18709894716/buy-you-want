<template>
  <el-container class="admin-layout">
    <!-- 左侧导航 -->
    <el-aside :width="isCollapse ? '64px' : '220px'" class="aside">
      <div class="logo">
        <h2 v-show="!isCollapse">BuyYouWant 商家中心</h2>
        <h2 v-show="isCollapse">BYW</h2>
      </div>
      <el-menu
        :default-active="currentRoute"
        router
        :collapse="isCollapse"
        class="side-menu"
      >
        <el-menu-item index="/dashboard">
          <el-icon><Odometer /></el-icon>
          <template #title>控制台</template>
        </el-menu-item>

        <el-sub-menu index="product">
          <template #title>
            <el-icon><Goods /></el-icon>
            <span>商品管理</span>
          </template>
          <el-menu-item index="/product/list">商品列表</el-menu-item>
          <el-menu-item index="/product/add">发布商品</el-menu-item>
        </el-sub-menu>

        <el-sub-menu index="order">
          <template #title>
            <el-icon><List /></el-icon>
            <span>订单管理</span>
          </template>
          <el-menu-item index="/order/list">订单列表</el-menu-item>
          <el-menu-item index="/order/after-sale">售后管理</el-menu-item>
        </el-sub-menu>

        <el-menu-item index="/im">
          <!-- 折叠态仅剩图标：用小红点提示；展开态未读数放标题右侧，避免遮挡文字 -->
          <el-badge :hidden="!imUnread || !isCollapse" is-dot class="im-menu-badge">
            <el-icon><Service /></el-icon>
          </el-badge>
          <template #title>
            <span>客服工作台</span>
            <span v-if="imUnread" class="im-unread-pill">{{ imUnread > 99 ? '99+' : imUnread }}</span>
          </template>
        </el-menu-item>

        <el-sub-menu index="promotion">
          <template #title>
            <el-icon><Present /></el-icon>
            <span>营销管理</span>
          </template>
          <el-menu-item index="/promotion/coupon">店铺优惠券</el-menu-item>
        </el-sub-menu>

        <el-sub-menu index="review">
          <template #title>
            <el-icon><ChatDotRound /></el-icon>
            <span>评价管理</span>
          </template>
          <el-menu-item index="/review/list">评价列表</el-menu-item>
        </el-sub-menu>

        <el-sub-menu index="shop">
          <template #title>
            <el-icon><Shop /></el-icon>
            <span>店铺管理</span>
          </template>
          <el-menu-item index="/shop/info">店铺设置</el-menu-item>
        </el-sub-menu>

        <el-sub-menu index="settle">
          <template #title>
            <el-icon><Wallet /></el-icon>
            <span>结算管理</span>
          </template>
          <el-menu-item index="/settle/index">结算与提现</el-menu-item>
        </el-sub-menu>
      </el-menu>
    </el-aside>

    <!-- 右侧内容 -->
    <el-container>
      <!-- 顶部 Header -->
      <el-header class="header">
        <div class="header-left">
          <el-icon class="collapse-btn" @click="toggleCollapse">
            <Fold v-if="!isCollapse" />
            <Expand v-else />
          </el-icon>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item v-if="$route.meta.title">{{ $route.meta.title }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-right">
          <el-dropdown @command="handleCommand">
            <span class="user-info">
              <el-icon><Avatar /></el-icon>
              <span class="username">{{ userStore.username || '商家' }}</span>
              <el-icon class="el-icon--right"><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <!-- 主体内容 -->
      <el-main class="main-content">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'
import request from '../utils/request'
import { connectIm, disconnectIm, addFrameHandler, removeFrameHandler } from '../utils/imSocket'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const isCollapse = ref(false)
const currentRoute = computed(() => route.path)

// ===== 全局客服未读角标（跨页面可见）=====
// 连接在布局层接管，离开客服页也保持长连接，据下推帧刷新未读总数（后端为准，避免本地漂移）
const imUnread = ref(0)
let unreadTimer: ReturnType<typeof setTimeout> | null = null

async function loadImUnread() {
  try {
    const total: any = await request.get('/im/unread-total')
    imUnread.value = Number(total) || 0
  } catch { /* ignore */ }
}

function scheduleReloadUnread() {
  if (unreadTimer) return
  unreadTimer = setTimeout(() => { unreadTimer = null; loadImUnread() }, 500)
}

function onImFrame(frame: Record<string, any>) {
  // 收到新消息或已读回执时，以后端未读总数为准重新同步角标
  if (frame?.action === 'message' || frame?.action === 'read') scheduleReloadUnread()
}

onMounted(() => {
  if (userStore.token) {
    connectIm()
    addFrameHandler(onImFrame)
    loadImUnread()
  }
})

onUnmounted(() => {
  removeFrameHandler(onImFrame)
  if (unreadTimer) { clearTimeout(unreadTimer); unreadTimer = null }
  disconnectIm()
})

const toggleCollapse = () => {
  isCollapse.value = !isCollapse.value
}

const handleCommand = (command: string) => {
  if (command === 'logout') {
    userStore.logout()
    router.push('/login')
  }
}
</script>

<style scoped lang="scss">
.admin-layout {
  height: 100vh;
  overflow: hidden;
}

.aside {
  background-color: #fff;
  border-right: 1px solid #f0f0f0;
  transition: width 0.3s;
  overflow: hidden;

  .logo {
    height: 60px;
    display: flex;
    align-items: center;
    justify-content: center;
    background-color: #fff;
    border-bottom: 1px solid #f5f5f5;

    h2 {
      color: var(--el-color-primary);
      margin: 0;
      font-size: 17px;
      white-space: nowrap;
      overflow: hidden;
    }
  }

  .side-menu {
    border-right: none;
    overflow-y: auto;
    height: calc(100vh - 60px);

    // C 端质感：圆角菜单块 + 浅红选中态（区别于 admin 整行高亮）
    :deep(.el-menu-item),
    :deep(.el-sub-menu__title) {
      margin: 4px 8px;
      border-radius: 8px;
      color: #606266;
    }

    :deep(.el-menu-item:hover),
    :deep(.el-sub-menu__title:hover) {
      background-color: #f5f6f7;
    }

    :deep(.el-menu-item.is-active) {
      background-color: var(--el-color-primary-light-9);
      color: var(--el-color-primary);
      font-weight: 500;
    }

    // 客服菜单未读角标：保持图标与标题的原有对齐（包裹 el-icon 后仍需 margin-right）
    .im-menu-badge {
      line-height: 1;
      :deep(.el-icon) {
        margin-right: 5px;
        vertical-align: middle;
      }
    }

    // 展开态未读数胶囊：跟在标题文字右侧，不遮挡菜单文字
    .im-unread-pill {
      display: inline-block;
      margin-left: 8px;
      padding: 0 5px;
      min-width: 16px;
      height: 16px;
      line-height: 16px;
      border-radius: 8px;
      background-color: var(--el-color-danger);
      color: #fff;
      font-size: 11px;
      text-align: center;
      vertical-align: middle;
    }

    &.el-menu--collapse {
      :deep(.el-menu-item),
      :deep(.el-sub-menu__title) {
        margin: 4px;
      }
    }
  }
}

.header {
  background-color: #fff;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;

  .header-left {
    display: flex;
    align-items: center;
    gap: 16px;

    .collapse-btn {
      font-size: 20px;
      cursor: pointer;
      color: #666;

      &:hover {
        color: var(--el-color-primary);
      }
    }
  }

  .header-right {
    .user-info {
      display: flex;
      align-items: center;
      gap: 6px;
      cursor: pointer;
      color: #333;
      padding: 6px 10px;
      border-radius: 8px;
      transition: background-color 0.2s;

      &:hover {
        background-color: #f5f6f7;
      }

      .username {
        font-size: 14px;
      }
    }
  }
}

.main-content {
  background-color: #f7f8fa;
  overflow-y: auto;
  padding: 20px;

  &::-webkit-scrollbar {
    width: 6px;
  }

  &::-webkit-scrollbar-thumb {
    background-color: #dcdfe6;
    border-radius: 3px;
  }
}
</style>
