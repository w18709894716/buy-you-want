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
        <h2 class="text-xl font-bold text-gray-800 mb-4">我的评价</h2>

        <!-- Tab 栏 -->
        <div class="bg-white rounded-t-lg border-b flex">
          <button
            v-for="tab in tabs"
            :key="tab.value"
            :class="[
              'px-6 py-3 text-sm font-medium border-b-2 transition-colors',
              activeTab === tab.value ? 'border-primary text-primary' : 'border-transparent text-gray-600 hover:text-gray-800'
            ]"
            @click="switchTab(tab.value)"
          >
            {{ tab.label }}
          </button>
        </div>

        <!-- 加载中 -->
        <div v-if="loading" class="bg-white rounded-b-lg p-12 text-center text-gray-400">
          加载中...
        </div>

        <!-- 空状态 -->
        <div v-else-if="reviews.length === 0" class="bg-white rounded-b-lg p-12 text-center text-gray-400">
          <p class="text-4xl mb-3">⭐</p>
          <p>暂无评价记录</p>
          <p class="text-xs mt-2">购买商品后，在这里发表评价</p>
        </div>

        <!-- 评价列表 -->
        <div v-else class="bg-white rounded-b-lg divide-y">
          <div v-for="review in reviews" :key="review.id" class="p-5">
            <!-- 评价头部 -->
            <div class="flex items-center justify-between mb-2">
              <div class="flex items-center gap-2">
                <!-- 星级 -->
                <span class="flex text-yellow-400 text-sm">
                  <span v-for="i in 5" :key="i" :class="i <= review.rating ? '' : 'text-gray-200'">★</span>
                </span>
                <span class="text-sm text-gray-600 font-medium">{{ ratingText(review.rating) }}</span>
              </div>
              <span class="text-xs text-gray-400">{{ formatDate(review.createdAt) }}</span>
            </div>

            <!-- 评价内容 -->
            <p v-if="review.content" class="text-sm text-gray-700 mb-2 leading-relaxed">{{ review.content }}</p>
            <p v-else class="text-sm text-gray-400 italic mb-2">该用户未填写评价内容</p>

            <!-- 评价图片 -->
            <div v-if="review.hasImage" class="flex gap-2 mb-2">
              <span class="inline-flex items-center gap-1 text-xs text-gray-400 bg-gray-50 px-2 py-1 rounded">
                🖼 有图评价
              </span>
            </div>

            <!-- 底部信息 -->
            <div class="flex items-center justify-between text-xs text-gray-400">
              <div class="flex items-center gap-3">
                <span>订单：{{ review.orderNo }}</span>
                <span v-if="review.isAnonymous">匿名评价</span>
                <NuxtLink
                  :to="`/product/${review.productId}`"
                  class="text-primary hover:underline"
                >查看商品 →</NuxtLink>
              </div>
              <span
                :class="review.status === 1 ? 'text-green-500' : 'text-gray-400'"
              >{{ review.status === 1 ? '已展示' : '已隐藏' }}</span>
            </div>
          </div>

          <!-- 分页 -->
          <div v-if="totalPages > 1" class="p-4 flex justify-center gap-2">
            <button
              :disabled="currentPage <= 1"
              class="px-3 py-1.5 border rounded text-sm disabled:opacity-50 hover:border-primary hover:text-primary transition-colors"
              @click="currentPage--; fetchReviews()"
            >上一页</button>
            <span class="px-3 py-1.5 text-sm text-gray-500">{{ currentPage }} / {{ totalPages }}</span>
            <button
              :disabled="currentPage >= totalPages"
              class="px-3 py-1.5 border rounded text-sm disabled:opacity-50 hover:border-primary hover:text-primary transition-colors"
              @click="currentPage++; fetchReviews()"
            >下一页</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useUserStore } from '~/stores/user'
import { get } from '~/utils/request'

definePageMeta({ middleware: ['auth'] })

const userStore = useUserStore()

const sidebarMenu = [
  { icon: '👤', label: '个人中心', path: '/user' },
  { icon: '📦', label: '我的订单', path: '/user/orders' },
  { icon: '📍', label: '收货地址', path: '/user/address' },
  { icon: '🎟️', label: '我的优惠券', path: '/user/coupons' },
  { icon: '⭐', label: '我的评价', path: '/user/reviews' },
  { icon: '❤️', label: '我的收藏', path: '/user/favorites' },
]

const tabs = [
  { label: '全部', value: 'all' },
  { label: '有图', value: 'image' },
]

const activeTab = ref('all')
const loading = ref(false)
const reviews = ref<any[]>([])
const currentPage = ref(1)
const totalPages = ref(1)

function ratingText(rating: number): string {
  const texts = ['', '非常差', '较差', '一般', '满意', '非常满意']
  return texts[rating] || ''
}

function formatDate(dateStr: string): string {
  if (!dateStr) return ''
  try {
    const d = new Date(dateStr)
    return d.toLocaleDateString('zh-CN') + ' ' + d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  } catch {
    return dateStr
  }
}

function switchTab(tab: string) {
  activeTab.value = tab
  currentPage.value = 1
  fetchReviews()
}

async function fetchReviews() {
  loading.value = true
  try {
    const hasImage = activeTab.value === 'image' ? true : undefined
    const data = await get('/review/my-reviews', {
      pageNum: currentPage.value,
      pageSize: 10,
      hasImage
    })
    reviews.value = data?.list || []
    totalPages.value = Math.ceil((data?.total || 0) / 10)
  } catch (e) {
    console.error('获取评价列表失败:', e)
    reviews.value = []
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  userStore.getUserInfo()
  fetchReviews()
})
</script>
