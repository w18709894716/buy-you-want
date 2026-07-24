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
        <h2 class="text-xl font-bold text-gray-800 mb-4">我的收藏</h2>
      
        <!-- 加载中 -->
        <div v-if="loading" class="bg-white rounded-lg p-12 text-center text-gray-400">
          <p>加载中...</p>
        </div>
      
        <!-- 空状态 -->
        <div v-else-if="!favorites.length" class="bg-white rounded-lg p-12 text-center text-gray-400">
          <p class="text-4xl mb-3">❤️</p>
          <p>暂无收藏商品</p>
          <p class="text-xs mt-2">浏览商品时，点击爱心图标即可收藏</p>
          <button
            class="mt-4 px-6 py-2 bg-primary text-white text-sm rounded-lg hover:bg-primary-600 transition-colors"
            @click="navigateTo('/search')"
          >
            去逛逛
          </button>
        </div>
      
        <!-- 收藏列表 -->
        <div v-else class="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-4">
          <div
            v-for="item in favorites"
            :key="item.favoriteId"
            class="bg-white rounded-lg overflow-hidden group relative"
          >
            <NuxtLink :to="`/product/${item.productId}`" class="block">
              <div class="aspect-square bg-gray-100 overflow-hidden relative">
                <img
                  :src="item.image || 'https://via.placeholder.com/300x300?text=商品图片'"
                  :alt="item.productName"
                  class="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
                />
                <span
                  v-if="!item.available"
                  class="absolute inset-0 bg-black/40 flex items-center justify-center text-white text-sm"
                >
                  已下架
                </span>
              </div>
              <div class="p-3">
                <h3 class="text-sm text-gray-800 line-clamp-2 min-h-[2.5rem] leading-tight">{{ item.productName }}</h3>
                <div class="mt-2 flex items-baseline gap-1">
                  <span class="text-primary text-lg font-bold">¥{{ formatPrice(item.price) }}</span>
                </div>
                <div class="mt-1 text-xs text-gray-400">{{ item.salesCount || 0 }}人已付款</div>
              </div>
            </NuxtLink>
            <button
              class="absolute top-2 right-2 w-8 h-8 rounded-full bg-white/80 backdrop-blur flex items-center justify-center shadow-sm hover:bg-white transition-colors text-primary"
              aria-label="取消收藏"
              @click.prevent.stop="onRemove(item)"
            >
              <svg xmlns="http://www.w3.org/2000/svg" class="w-4 h-4" fill="currentColor" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                <path stroke-linecap="round" stroke-linejoin="round" d="M4.318 6.318a4.5 4.5 0 000 6.364L12 20.364l7.682-7.682a4.5 4.5 0 00-6.364-6.364L12 7.636l-1.318-1.318a4.5 4.5 0 00-6.364 0z" />
              </svg>
            </button>
          </div>
        </div>
      
        <!-- 分页 -->
        <div v-if="!loading && total > pageSize" class="flex justify-center gap-2 mt-6">
          <button
            class="px-4 py-1.5 text-sm rounded border border-gray-200 bg-white disabled:opacity-40 hover:border-primary hover:text-primary transition-colors"
            :disabled="pageNum <= 1"
            @click="changePage(pageNum - 1)"
          >
            上一页
          </button>
          <span class="px-3 py-1.5 text-sm text-gray-500">{{ pageNum }} / {{ totalPages }}</span>
          <button
            class="px-4 py-1.5 text-sm rounded border border-gray-200 bg-white disabled:opacity-40 hover:border-primary hover:text-primary transition-colors"
            :disabled="pageNum >= totalPages"
            @click="changePage(pageNum + 1)"
          >
            下一页
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useUserStore } from '~/stores/user'

definePageMeta({ middleware: ['auth'] })

const userStore = useUserStore()
const { toggleFavorite } = useFavorites()

const sidebarMenu = [
  { icon: '👤', label: '个人中心', path: '/user' },
  { icon: '📦', label: '我的订单', path: '/user/orders' },
  { icon: '📍', label: '收货地址', path: '/user/address' },
  { icon: '🎟️', label: '我的优惠券', path: '/user/coupons' },
  { icon: '⭐', label: '我的评价', path: '/user/reviews' },
  { icon: '❤️', label: '我的收藏', path: '/user/favorites' },
]

interface FavoriteItem {
  favoriteId: number
  productId: number
  productName: string
  image: string | null
  price: number | null
  salesCount: number
  available: boolean
}

const favorites = ref<FavoriteItem[]>([])
const loading = ref(true)
const pageNum = ref(1)
const pageSize = ref(12)
const total = ref(0)
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))

function formatPrice(price: number | null): string {
  if (price == null) return '0.00'
  return Number(price).toFixed(2)
}

async function fetchFavorites() {
  loading.value = true
  try {
    const data: any = await get('/user/favorite/list', { pageNum: pageNum.value, pageSize: pageSize.value })
    favorites.value = data?.list || []
    total.value = data?.total || 0
  } catch (e) {
    console.error('获取收藏列表失败:', e)
    favorites.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function changePage(target: number) {
  if (target < 1 || target > totalPages.value) return
  pageNum.value = target
  fetchFavorites()
}

async function onRemove(item: FavoriteItem) {
  await toggleFavorite(item.productId)
  favorites.value = favorites.value.filter(f => f.favoriteId !== item.favoriteId)
  total.value = Math.max(0, total.value - 1)
  // 当前页删空且非首页时回退一页
  if (!favorites.value.length && pageNum.value > 1) {
    changePage(pageNum.value - 1)
  }
}

onMounted(() => {
  userStore.getUserInfo()
  fetchFavorites()
})
</script>
