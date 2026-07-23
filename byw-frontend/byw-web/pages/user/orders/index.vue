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
        <h2 class="text-xl font-bold text-gray-800 mb-4">我的订单</h2>

        <!-- Tab 栏 -->
        <div class="bg-white rounded-t-lg border-b flex overflow-x-auto scrollbar-hide">
          <button
            v-for="tab in tabs"
            :key="tab.value"
            :class="[
              'px-3 sm:px-6 py-3 text-sm font-medium border-b-2 transition-colors whitespace-nowrap flex-shrink-0',
              activeTab === tab.value ? 'border-primary text-primary' : 'border-transparent text-gray-600 hover:text-gray-800'
            ]"
            @click="switchTab(tab.value)"
          >
            {{ tab.label }}
            <span v-if="tab.count" class="ml-1 text-xs bg-gray-100 text-gray-500 px-1.5 py-0.5 rounded-full">{{ tab.count }}</span>
          </button>
        </div>

        <!-- 评价子筛选（仅“评价”tab下显示） -->
        <div v-if="activeTab === 'review'" class="bg-white border-b px-3 sm:px-6 py-2 flex gap-2">
          <button
            v-for="sub in reviewSubTabs"
            :key="sub.value"
            :class="[
              'px-3 py-1 text-xs rounded-full border transition-colors',
              reviewSubTab === sub.value ? 'bg-primary text-white border-primary' : 'text-gray-500 border-gray-200 hover:border-primary hover:text-primary'
            ]"
            @click="switchReviewSubTab(sub.value)"
          >{{ sub.label }}</button>
        </div>

        <!-- 订单列表 -->
        <div class="bg-white rounded-b-lg">
          <div v-if="filteredOrders.length === 0" class="p-16 text-center">
            <div class="text-5xl mb-3">📦</div>
            <p class="text-gray-400">暂无相关订单</p>
          </div>

          <div v-else class="divide-y">
            <div v-for="order in filteredOrders" :key="order.id" class="p-4">
              <!-- 订单头部 -->
              <div class="flex flex-wrap items-center justify-between text-sm text-gray-500 mb-3 pb-3 border-b border-gray-100 gap-2">
                <div class="flex items-center gap-2 sm:gap-4 flex-wrap">
                  <span>订单号：{{ order.orderNo || order.id }}</span>
                  <span>{{ order.date }}</span>
                </div>
                <span :class="order.statusClass" class="font-medium">{{ order.statusText }}</span>
              </div>

              <!-- 订单商品（支持多件） -->
              <div class="space-y-3 mb-3">
                <div
                  v-for="(item, idx) in order.items"
                  :key="item.skuId || idx"
                  class="flex items-center gap-4"
                >
                  <NuxtLink :to="`/product/${item.productId}`">
                    <img :src="item.productImage || 'https://via.placeholder.com/80x80?text=商品'" :alt="item.productName" class="w-20 h-20 object-cover rounded" />
                  </NuxtLink>
                  <div class="flex-1">
                    <NuxtLink :to="`/product/${item.productId}`" class="text-sm text-gray-800 hover:text-primary">
                      {{ item.productName }}
                    </NuxtLink>
                    <p class="text-xs text-gray-400 mt-1">{{ item.skuName }}</p>
                  </div>
                  <div class="text-right">
                    <div class="text-sm font-bold text-primary">¥{{ (item.price || 0).toFixed(2) }}</div>
                    <div class="text-xs text-gray-400">x{{ item.quantity }}</div>
                  </div>
                </div>
              </div>

              <!-- 已评价摘要 -->
              <div
                v-if="activeTab === 'review' && reviewSubTab === 'reviewed' && reviewSummaries[order.orderNo]"
                class="mb-3 bg-gray-50 rounded-lg p-3"
              >
                <div class="flex items-center gap-1 mb-1">
                  <span
                    v-for="s in 5"
                    :key="s"
                    class="text-sm"
                    :class="s <= (reviewSummaries[order.orderNo].rating || 0) ? 'text-yellow-400' : 'text-gray-200'"
                  >★</span>
                  <span class="ml-2 text-xs text-gray-400">我的评价</span>
                </div>
                <p class="text-sm text-gray-600 line-clamp-2">{{ reviewSummaries[order.orderNo].content || '（未填写评价内容）' }}</p>
                <p v-if="reviewSummaries[order.orderNo].appendContent" class="text-xs text-gray-500 mt-1 line-clamp-2">
                  <span class="text-primary">追评：</span>{{ reviewSummaries[order.orderNo].appendContent }}
                </p>
              </div>

              <!-- 订单底部 -->
              <div class="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3">
                <div class="text-sm text-gray-500">
                  共 {{ order.quantity }} 件商品，合计：
                  <span class="text-primary font-bold text-lg">¥{{ order.total.toFixed(2) }}</span>
                  <!-- 待付款倒计时 -->
                  <span v-if="order.status === 0 && getOrderRemainingSeconds(order) > 0" class="ml-3 text-orange-500 text-xs">
                    剩余 {{ formatCountdown(getOrderRemainingSeconds(order)) }}
                  </span>
                  <span v-if="order.status === 0 && getOrderRemainingSeconds(order) <= 0" class="ml-3 text-gray-400 text-xs">
                    已超时
                  </span>
                </div>
                <div class="flex flex-wrap gap-2">
                  <button
                    v-if="order.status === 0 && getOrderRemainingSeconds(order) > 0"
                    class="px-4 py-1.5 bg-primary text-white text-sm rounded hover:bg-primary-600 transition-colors"
                    @click="handlePay(order)"
                  >
                    立即付款
                  </button>
                  <button
                    v-if="order.status === 2"
                    class="px-4 py-1.5 bg-primary text-white text-sm rounded hover:bg-primary-600 transition-colors"
                    @click="handleConfirmReceive(order)"
                  >
                    确认收货
                  </button>
                  <button
                    v-if="order.status === 3 && (!order.reviewed || order.reviewed === 0)"
                    class="px-4 py-1.5 border border-primary text-primary text-sm rounded hover:bg-primary-50 transition-colors"
                    @click="handleReview(order)"
                  >
                    去评价
                  </button>
                  <button
                    v-if="order.status === 3 && order.reviewed === 1"
                    class="px-4 py-1.5 border border-primary text-primary text-sm rounded hover:bg-primary-50 transition-colors"
                    @click="handleAppendReview(order)"
                  >
                    继续追评
                  </button>
                  <button
                    class="px-4 py-1.5 border border-gray-300 text-gray-600 text-sm rounded hover:bg-gray-50 transition-colors"
                    @click="viewOrderDetail(order)"
                  >
                    查看详情
                  </button>
                  <button
                    v-if="order.status === 0"
                    class="px-4 py-1.5 border border-gray-300 text-gray-500 text-sm rounded hover:bg-gray-50 transition-colors"
                    @click="handleCancelOrder(order)"
                  >
                    取消订单
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 分页 -->
        <div v-if="totalPages > 1" class="mt-6 flex justify-center gap-2">
          <button
            :disabled="currentPage <= 1"
            class="px-4 py-2 border rounded text-sm disabled:opacity-50 hover:border-primary hover:text-primary transition-colors"
            @click="currentPage--"
          >
            上一页
          </button>
          <button
            v-for="page in totalPages"
            :key="page"
            :class="[
              'px-4 py-2 border rounded text-sm transition-colors',
              page === currentPage ? 'bg-primary text-white border-primary' : 'hover:border-primary hover:text-primary'
            ]"
            @click="currentPage = page"
          >
            {{ page }}
          </button>
          <button
            :disabled="currentPage >= totalPages"
            class="px-4 py-2 border rounded text-sm disabled:opacity-50 hover:border-primary hover:text-primary transition-colors"
            @click="currentPage++"
          >
            下一页
          </button>
        </div>
      </div>
    </div>

    <!-- 确认弹窗 -->
    <Teleport to="body">
      <Transition name="modal">
        <div v-if="confirmDialog" class="fixed inset-0 z-50 flex items-center justify-center p-4">
          <div class="fixed inset-0 bg-black/40" @click="confirmDialog = null" />
          <div class="relative bg-white rounded-lg shadow-xl w-full max-w-sm p-6">
            <h3 class="text-base font-medium text-gray-800 mb-2">{{ confirmDialog.title }}</h3>
            <p class="text-sm text-gray-500 mb-5">{{ confirmDialog.message }}</p>
            <div class="flex justify-end gap-3">
              <button
                class="px-4 h-9 text-sm text-gray-600 border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors"
                @click="confirmDialog = null"
              >取消</button>
              <button
                class="px-4 h-9 text-sm text-white bg-primary rounded-lg hover:bg-primary-600 transition-colors"
                @click="confirmDialog.onConfirm()"
              >确定</button>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { useUserStore } from '~/stores/user'
import { get, post } from '~/utils/request'

definePageMeta({ middleware: ['auth'] })

const userStore = useUserStore()
const route = useRoute()

const sidebarMenu = [
  { icon: '👤', label: '个人中心', path: '/user' },
  { icon: '📦', label: '我的订单', path: '/user/orders' },
  { icon: '📍', label: '收货地址', path: '/user/address' },
  { icon: '🎟️', label: '我的优惠券', path: '/user/coupons' },
  { icon: '⭐', label: '我的评价', path: '/user/reviews' },
  { icon: '❤️', label: '我的收藏', path: '/user/favorites' },
]

const activeTab = ref((route.query.status as string) || 'all')
const reviewSubTab = ref('pending') // pending=待评价, reviewed=已评价
const currentPage = ref(1)
const totalPages = ref(1)

const reviewSubTabs = [
  { label: '待评价', value: 'pending' },
  { label: '已评价', value: 'reviewed' },
]

const tabs = ref([
  { label: '全部订单', value: 'all', count: 0 },
  { label: '待付款', value: '0', count: 0 },
  { label: '待发货', value: '1', count: 0 },
  { label: '待收货', value: '2', count: 0 },
  { label: '评价', value: 'review', count: 0 },
  { label: '已取消', value: '4', count: 0 },
])

const statusTextMap: Record<number, string> = {
  0: '待付款',
  1: '待发货',
  2: '待收货',
  3: '已完成',
  4: '已取消',
  5: '退款中',
  6: '已退款',
  7: '部分发货'
}

const statusClassMap: Record<number, string> = {
  0: 'text-red-500',
  1: 'text-orange-500',
  2: 'text-blue-500',
  3: 'text-green-500',
  4: 'text-gray-500',
  5: 'text-yellow-500',
  6: 'text-gray-400',
  7: 'text-orange-500'
}

// 订单列表从接口获取
const orders = ref<any[]>([])
// 已评价订单的评价摘要：orderNo -> { rating, content, appendContent }
const reviewSummaries = ref<Record<string, any>>({})

const fetchOrders = async () => {
  try {
    // 构建查询参数
    let statusParam: number | undefined = undefined
    let reviewedParam: number | undefined = undefined
    if (activeTab.value === 'review') {
      statusParam = 3
      reviewedParam = reviewSubTab.value === 'reviewed' ? 1 : 0
    } else if (activeTab.value !== 'all') {
      statusParam = parseInt(activeTab.value)
    }
    const data = await get('/order/my-orders', {
      pageNum: currentPage.value,
      pageSize: 10,
      status: statusParam,
      reviewed: reviewedParam
    })
    orders.value = (data?.list || []).map((o: any) => {
      const items = (o.items && o.items.length ? o.items : [{
        productId: o.productId,
        productImage: o.productImage,
        productName: o.productName,
        skuName: '',
        price: o.totalAmount,
        quantity: 1
      }])
      const totalQuantity = items.reduce((s: number, it: any) => s + (it.quantity || 0), 0)
      return {
        id: o.id,
        orderNo: o.orderNo,
        date: o.createdAt,
        createdAt: o.createdAt,
        status: o.status,
        reviewed: o.reviewed,
        statusText: statusTextMap[o.status] || '未知',
        statusClass: statusClassMap[o.status] || 'text-gray-500',
        items,
        // 保留首件商品用于评价等跳转场景
        productId: items[0]?.productId,
        productName: items[0]?.productName,
        quantity: totalQuantity,
        total: o.payAmount || o.totalAmount
      }
    })
    totalPages.value = Math.ceil((data?.total || 0) / 10)
    // 已评价子筛选：拉取评价摘要用于卡片展示
    if (activeTab.value === 'review' && reviewSubTab.value === 'reviewed') {
      fetchReviewSummaries()
    } else {
      reviewSummaries.value = {}
    }
  } catch (e) {
    console.error('获取订单列表失败:', e)
    orders.value = []
  }
}

// 拉取当前已评价订单的评价摘要（取首条评价）
async function fetchReviewSummaries() {
  reviewSummaries.value = {}
  const targets = orders.value.filter(o => o.orderNo)
  await Promise.all(targets.map(async (o) => {
    try {
      const list = await get<any[]>(`/review/order/${o.orderNo}`)
      if (list && list.length) {
        const first = list[0]
        reviewSummaries.value[o.orderNo] = {
          rating: first.rating || 0,
          content: first.content || '',
          appendContent: first.appendContent || ''
        }
      }
    } catch (e) {
      // 单条失败不阻断其他
    }
  }))
}

// 独立获取各状态订单数量（不受当前Tab筛选影响）
async function fetchOrderCounts() {
  try {
    const counts = await get<Record<number, number>>('/order/status-counts')
    if (counts) {
      tabs.value[1].count = counts[0] || 0  // 待付款
      tabs.value[2].count = counts[1] || 0  // 待发货
      tabs.value[3].count = counts[2] || 0  // 待收货
      tabs.value[4].count = counts[3] || 0  // 待评价
      tabs.value[5].count = counts[4] || 0  // 已取消
      // “全部订单” tab 显示所有状态总和
      tabs.value[0].count = Object.values(counts).reduce((a: number, b: number) => a + b, 0)
    }
  } catch (e) {
    console.error('获取订单统计失败:', e)
  }
}

const filteredOrders = computed(() => {
  // 后端已按状态筛选，前端直接展示
  return orders.value
})

function switchTab(tab: string) {
  activeTab.value = tab
  reviewSubTab.value = 'pending'
  currentPage.value = 1
  fetchOrders()
  fetchOrderCounts()
}

/** 切换评价子筛选（待评价/已评价） */
function switchReviewSubTab(sub: string) {
  if (reviewSubTab.value === sub) return
  reviewSubTab.value = sub
  currentPage.value = 1
  fetchOrders()
}

/** 查看订单详情 */
function viewOrderDetail(order: any) {
  navigateTo(`/user/orders/${order.orderNo || order.id}`)
}

/** 立即付款 - 跳转到支付页面 */
function handlePay(order: any) {
  navigateTo(`/payment/${order.orderNo}`)
}

// ===== 待付款倒计时（30分钟） =====
const TIMEOUT_MS = 30 * 60 * 1000
const now = ref(Date.now())
let countdownTimer: ReturnType<typeof setInterval> | null = null

function getOrderRemainingSeconds(order: any): number {
  if (!order.createdAt || order.status !== 0) return 0
  const deadline = new Date(order.createdAt).getTime() + TIMEOUT_MS
  return Math.max(0, Math.floor((deadline - now.value) / 1000))
}

function formatCountdown(seconds: number): string {
  const m = Math.floor(seconds / 60)
  const s = seconds % 60
  return `${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`
}

onMounted(() => {
  userStore.getUserInfo()
  fetchOrders()
  fetchOrderCounts()
  // 每秒更新倒计时
  countdownTimer = setInterval(() => {
    now.value = Date.now()
  }, 1000)
})

onUnmounted(() => {
  if (countdownTimer) clearInterval(countdownTimer)
})

const confirmDialog = ref<{ title: string; message: string; onConfirm: () => void } | null>(null)

/** 确认收货 */
function handleConfirmReceive(order: any) {
  confirmDialog.value = {
    title: '确认收货',
    message: '确认已收到该商品？',
    onConfirm: async () => {
      confirmDialog.value = null
      try {
        await post(`/order/confirm/${order.orderNo || order.id}`)
        fetchOrders()
      } catch (e) {
        console.error('确认收货失败:', e)
      }
    }
  }
}

/** 去评价 */
function handleReview(order: any) {
  navigateTo(`/user/orders/${order.orderNo || order.id}/review`)
}

/** 继续追评 */
function handleAppendReview(order: any) {
  navigateTo(`/user/orders/${order.orderNo || order.id}/review?mode=append`)
}

/** 取消订单 */
function handleCancelOrder(order: any) {
  confirmDialog.value = {
    title: '取消订单',
    message: '确定要取消该订单吗？',
    onConfirm: async () => {
      confirmDialog.value = null
      try {
        await post(`/order/cancel/${order.orderNo || order.id}`, null, { params: { reason: '用户主动取消' } })
        fetchOrders()
      } catch (e) {
        console.error('取消订单失败:', e)
      }
    }
  }
}
</script>
