<template>
  <div class="min-h-screen flex flex-col bg-gray-50">
    <!-- 全局登录弹框（未登录触发需登录操作时原地弹出，不跳转登录页） -->
    <LoginModal />
    <!-- 顶部导航栏 -->
    <header class="bg-white shadow-sm sticky top-0 z-50">
      <!-- 顶部条 - 用户信息 -->
      <div class="bg-gray-100 text-xs text-gray-500 hidden sm:block">
        <div class="max-w-7xl mx-auto px-4 flex justify-between items-center h-8">
          <div>
            <!-- 已登录：悬浮用户名展开退出登录，箭头提示可展开 -->
            <div v-if="userStore.username" class="relative group inline-block">
              <span class="inline-flex items-center gap-1 h-8 cursor-pointer select-none">
                欢迎回来，<span class="text-primary">{{ userStore.nickname || userStore.username }}</span>
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor"
                  class="w-3 h-3 text-gray-400 transition-transform duration-200 group-hover:rotate-180 group-hover:text-primary">
                  <path fill-rule="evenodd" d="M5.23 7.21a.75.75 0 011.06.02L10 11.168l3.71-3.938a.75.75 0 111.08 1.04l-4.25 4.5a.75.75 0 01-1.08 0l-4.25-4.5a.75.75 0 01.02-1.06z" clip-rule="evenodd" />
                </svg>
              </span>
              <!-- 下拉面板：右对齐紧贴用户名下方，顶部直角与用户名区域连为一体 -->
              <div class="absolute right-0 top-full hidden group-hover:block z-50">
                <div class="bg-white rounded-b shadow-md border border-gray-100">
                  <button
                    class="whitespace-nowrap px-3 py-1.5 text-xs text-gray-500 hover:text-red-500 transition-colors"
                    @click="handleLogout"
                  >
                    退出登录
                  </button>
                </div>
              </div>
            </div>
            <span v-else>
              <NuxtLink to="/login" class="hover:text-primary">登录</NuxtLink>
              <span class="mx-2">|</span>
              <NuxtLink to="/register" class="hover:text-primary">注册</NuxtLink>
            </span>
          </div>
          <div class="flex items-center gap-4">
            <a :href="merchantApplyUrl" target="_blank" class="hover:text-primary">商家入驻</a>
            <NuxtLink to="/user/orders" class="hover:text-primary">我的订单</NuxtLink>
            <NuxtLink to="/user" class="hover:text-primary">个人中心</NuxtLink>
          </div>
        </div>
      </div>

      <!-- 主头部 -->
      <div class="max-w-7xl mx-auto px-4 py-3 md:py-4 flex items-center gap-2 sm:gap-3 md:gap-8">
        <!-- Logo -->
        <NuxtLink to="/" class="flex-shrink-0 inline-flex items-center">
          <h1 class="text-base sm:text-lg md:text-2xl font-bold text-primary leading-none">BuyYouWant</h1>
          <p class="text-xs text-gray-400 ml-1 hidden sm:block">买你所想</p>
        </NuxtLink>

        <!-- 搜索栏 -->
        <div class="flex-1 min-w-0 mx-1 sm:mx-0">
          <SearchBar />
        </div>

        <!-- 购物车图标 -->
        <NuxtLink
          to="/cart"
          class="relative flex items-center gap-1 md:gap-2 px-2 sm:px-3 md:px-4 py-2 border border-primary text-primary rounded-full hover:bg-primary hover:text-white transition-colors flex-shrink-0"
        >
          <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" viewBox="0 0 20 20" fill="currentColor">
            <path d="M3 1a1 1 0 000 2h1.22l.305 1.222a.997.997 0 00.01.042l1.358 5.43-.893.892C3.74 11.846 4.632 14 6.414 14H15a1 1 0 000-2H6.414l1-1H14a1 1 0 00.894-.553l3-6A1 1 0 0017 3H6.28l-.31-1.243A1 1 0 005 1H3zM16 16.5a1.5 1.5 0 11-3 0 1.5 1.5 0 013 0zM6.5 18a1.5 1.5 0 100-3 1.5 1.5 0 000 3z" />
          </svg>
          <span class="text-sm font-medium hidden sm:inline">购物车</span>
          <span
            v-if="cartStore.totalCount > 0"
            class="absolute -top-2 -right-2 bg-red-500 text-white text-xs rounded-full h-5 w-5 flex items-center justify-center"
          >
            {{ cartStore.totalCount > 99 ? '99+' : cartStore.totalCount }}
          </span>
        </NuxtLink>
      </div>

      <!-- 分类导航 -->
      <nav class="border-t border-gray-100">
        <div class="max-w-7xl mx-auto px-4">
          <ul class="flex items-center gap-4 md:gap-6 h-10 text-sm overflow-x-auto whitespace-nowrap scrollbar-hide">
            <li class="flex-shrink-0">
              <NuxtLink to="/" :class="isHome ? 'text-primary font-medium' : 'text-gray-600 hover:text-primary'">首页</NuxtLink>
            </li>
            <li v-for="cat in categories" :key="cat.id" class="flex-shrink-0">
              <NuxtLink
                :to="`/search?category=${cat.name}`"
                :class="activeCategory === cat.name ? 'text-primary font-medium' : 'text-gray-600 hover:text-primary'"
              >{{ cat.name }}</NuxtLink>
            </li>
          </ul>
        </div>
      </nav>
    </header>

    <!-- 主内容区 -->
    <main class="flex-1">
      <slot />
    </main>

    <!-- 悬浮客服入口 + 未读角标 -->
    <button
      class="fixed bottom-6 right-6 z-[55] w-14 h-14 rounded-full bg-primary text-white shadow-lg flex items-center justify-center hover:bg-primary-600 transition-colors"
      title="联系客服"
      @click="imStore.togglePanel()"
    >
      <svg xmlns="http://www.w3.org/2000/svg" class="w-7 h-7" viewBox="0 0 24 24" fill="currentColor">
        <path d="M12 3C6.48 3 2 6.94 2 11.5c0 2.3 1.16 4.37 3.03 5.86-.13 1.03-.5 2.3-1.2 3.4-.16.25.05.58.34.5 1.85-.5 3.2-1.2 4.02-1.74.86.2 1.77.32 2.71.32 5.52 0 10-3.94 10-8.5S17.52 3 12 3z" />
      </svg>
      <span
        v-if="imStore.unreadTotal > 0"
        class="absolute -top-1 -right-1 bg-red-500 text-white text-xs rounded-full h-5 min-w-[20px] px-1 flex items-center justify-center"
      >
        {{ imStore.unreadTotal > 99 ? '99+' : imStore.unreadTotal }}
      </span>
    </button>
    <ImChatPanel />

    <!-- 页脚 -->
    <footer class="bg-gray-800 text-gray-400 mt-12">
      <div class="max-w-7xl mx-auto px-4 py-10">
        <div class="grid grid-cols-2 md:grid-cols-4 gap-6 md:gap-8 mb-8">
          <div>
            <h3 class="text-white font-medium mb-4">购物指南</h3>
            <ul class="space-y-2 text-sm">
              <li><a href="#" class="hover:text-white">购物流程</a></li>
              <li><a href="#" class="hover:text-white">会员制度</a></li>
              <li><a href="#" class="hover:text-white">常见问题</a></li>
            </ul>
          </div>
          <div>
            <h3 class="text-white font-medium mb-4">配送方式</h3>
            <ul class="space-y-2 text-sm">
              <li><a href="#" class="hover:text-white">配送范围</a></li>
              <li><a href="#" class="hover:text-white">配送费用</a></li>
              <li><a href="#" class="hover:text-white">配送时效</a></li>
            </ul>
          </div>
          <div>
            <h3 class="text-white font-medium mb-4">支付方式</h3>
            <ul class="space-y-2 text-sm">
              <li><a href="#" class="hover:text-white">在线支付</a></li>
              <li><a href="#" class="hover:text-white">货到付款</a></li>
              <li><a href="#" class="hover:text-white">分期付款</a></li>
            </ul>
          </div>
          <div>
            <h3 class="text-white font-medium mb-4">售后服务</h3>
            <ul class="space-y-2 text-sm">
              <li><a href="#" class="hover:text-white">退换货政策</a></li>
              <li><a href="#" class="hover:text-white">退换货流程</a></li>
              <li><a href="#" class="hover:text-white">联系客服</a></li>
            </ul>
          </div>
        </div>
        <div class="border-t border-gray-700 pt-6 text-center text-sm">
          <p>© 2026 BuyYouWant 买你所想 - 版权所有</p>
        </div>
      </div>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { useUserStore } from '~/stores/user'
import { useCartStore } from '~/stores/cart'
import { useImStore } from '~/stores/im'
import { get } from '~/utils/request'

const userStore = useUserStore()
const cartStore = useCartStore()
const imStore = useImStore()
const route = useRoute()
// 商家入驻已迁至商家中心（byw-merchant-web），导航外链跳转
const merchantApplyUrl = `${useRuntimeConfig().public.merchantWebUrl}/apply`

// 导航高亮状态：首页仅在根路径高亮，分类仅在搜索页且 category 参数匹配时高亮
const isHome = computed(() => route.path === '/')
const activeCategory = computed(() =>
  route.path === '/search' ? ((route.query.category as string) || '') : ''
)

const categories = ref<{ id: number; name: string }[]>([])

// 退出登录：清空登录态并跳转登录页（store 内部处理）
function handleLogout() {
  userStore.logout()
}

async function fetchNavCategories() {
  try {
    const data = await get<any[]>('/product/category/tree')
    // 只取一级分类（parentId 为 0 或 null 的根节点）
    categories.value = (data || [])
      .filter(c => !c.parentId || c.parentId === 0)
      .map(c => ({ id: c.id, name: c.name }))
  } catch (e) {
    console.error('获取导航分类失败:', e)
  }
}

onMounted(fetchNavCategories)

// 登录态就绪后初始化 IM（建立 WS、拉取未读）；退出登录时断开
onMounted(() => {
  if (userStore.isLoggedIn) imStore.init()
})
watch(() => userStore.isLoggedIn, (logged) => {
  if (logged) {
    imStore.init()
  } else {
    imStore.teardown()
  }
})

// 空闲超时会断开 IM 长连接，届时无法靠 WS 推送刷新角标。
// 改为：切回标签页(focus) + 路由切换时各拉一次未读，再加 60s 低频兜底定时器。
let unreadBackupTimer: any = null
function refreshUnread() {
  if (userStore.isLoggedIn) imStore.loadUnreadTotal()
}
watch(() => route.fullPath, refreshUnread)
onMounted(() => {
  window.addEventListener('focus', refreshUnread)
  unreadBackupTimer = setInterval(refreshUnread, 60000)
})
onUnmounted(() => {
  window.removeEventListener('focus', refreshUnread)
  if (unreadBackupTimer) { clearInterval(unreadBackupTimer); unreadBackupTimer = null }
})
</script>
