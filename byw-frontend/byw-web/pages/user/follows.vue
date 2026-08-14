<template>
  <div class="max-w-7xl mx-auto px-4 py-6">
    <div class="flex gap-6">
      <!-- 侧边栏 -->
      <aside class="w-52 flex-shrink-0 hidden md:block">
        <div class="bg-white rounded-lg p-4 mb-4">
          <div class="flex items-center gap-3">
            <div class="w-14 h-14 bg-primary-100 rounded-full flex items-center justify-center text-primary text-xl font-bold">
              {{ (userStore.nickname || userStore.username || '?')[0] }}
            </div>
            <div>
              <div class="font-medium text-gray-800">{{ userStore.nickname || userStore.username }}</div>
              <div class="text-xs text-gray-400">{{ userStore.username }}</div>
            </div>
          </div>
        </div>
        <nav class="bg-white rounded-lg overflow-hidden">
          <NuxtLink
            v-for="item in sidebarMenu"
            :key="item.path"
            :to="item.path"
            :class="[
              'flex items-center gap-3 px-4 py-3 text-sm transition-colors',
              $route.path === item.path ? 'bg-primary-50 text-primary border-l-2 border-primary' : 'text-gray-600 hover:bg-gray-50'
            ]"
          >
            <span class="text-lg">{{ item.icon }}</span>
            <span>{{ item.label }}</span>
          </NuxtLink>
        </nav>
      </aside>

      <!-- 主内容区 -->
      <div class="flex-1">
        <h2 class="text-xl font-bold text-gray-800 mb-4">我的关注</h2>

        <!-- 加载中 -->
        <div v-if="loading" class="bg-white rounded-lg p-12 text-center text-gray-400">
          <p>加载中...</p>
        </div>

        <!-- 空状态 -->
        <div v-else-if="!shops.length" class="bg-white rounded-lg p-12 text-center text-gray-400">
          <p class="text-4xl mb-3">🏬</p>
          <p>暂无关注的店铺</p>
          <p class="text-xs mt-2">进入店铺主页，点击「+ 关注」即可关注店铺</p>
          <button
            class="mt-4 px-6 py-2 bg-primary text-white text-sm rounded-lg hover:bg-primary-600 transition-colors"
            @click="navigateTo('/search')"
          >
            去逛逛
          </button>
        </div>

        <!-- 关注店铺列表 -->
        <div v-else class="space-y-3">
          <div
            v-for="shop in shops"
            :key="shop.shopId"
            class="bg-white rounded-lg p-4 flex items-center gap-4"
          >
            <img
              :src="shop.logo || 'https://via.placeholder.com/96x96?text=店铺'"
              alt="店铺Logo"
              class="w-12 h-12 rounded-lg object-cover bg-gray-100 flex-shrink-0"
            />
            <div class="flex-1 min-w-0">
              <div class="font-medium text-gray-800 truncate">{{ shop.shopName || ('店铺 ' + shop.shopId) }}</div>
            </div>
            <NuxtLink
              :to="`/shop/${shop.shopId}`"
              class="flex-shrink-0 h-8 px-4 bg-primary text-white text-sm rounded-full flex items-center hover:bg-primary-600 transition-colors"
            >
              进店逛逛
            </NuxtLink>
            <button
              class="flex-shrink-0 h-8 px-4 border border-gray-200 text-gray-500 text-sm rounded-full hover:border-red-400 hover:text-red-500 transition-colors"
              :disabled="shop._unfollowing"
              @click="unfollow(shop)"
            >
              {{ shop._unfollowing ? '取消中...' : '取消关注' }}
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { get, del } from '~/utils/request'
import { useUserStore } from '~/stores/user'

const userStore = useUserStore()

const sidebarMenu = [
  { icon: '👤', label: '个人中心', path: '/user' },
  { icon: '📦', label: '我的订单', path: '/user/orders' },
  { icon: '📍', label: '收货地址', path: '/user/address' },
  { icon: '🎟️', label: '我的优惠券', path: '/user/coupons' },
  { icon: '⭐', label: '我的评价', path: '/user/reviews' },
  { icon: '❤️', label: '我的收藏', path: '/user/favorites' },
  { icon: '🏬', label: '我的关注', path: '/user/follows' },
]

interface FollowedShop {
  shopId: number
  shopName?: string
  logo?: string
  _unfollowing?: boolean
}

const loading = ref(true)
const shops = ref<FollowedShop[]>([])

async function loadFollows() {
  loading.value = true
  try {
    shops.value = (await get('/user/shop-follow/list')) || []
  } catch (e) {
    console.error('获取关注列表失败:', e)
    shops.value = []
  } finally {
    loading.value = false
  }
}

async function unfollow(shop: FollowedShop) {
  shop._unfollowing = true
  try {
    await del(`/user/shop-follow/${shop.shopId}`)
    shops.value = shops.value.filter(s => s.shopId !== shop.shopId)
  } catch (e: any) {
    if (!e?.silent) console.error('取消关注失败:', e)
  } finally {
    shop._unfollowing = false
  }
}

onMounted(() => {
  loadFollows()
})
</script>
