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

              <!-- 订单商品 -->
              <div class="flex items-center gap-4 mb-3">
                <NuxtLink :to="`/product/${order.productId}`">
                  <img :src="order.image" :alt="order.productName" class="w-20 h-20 object-cover rounded" />
                </NuxtLink>
                <div class="flex-1">
                  <NuxtLink :to="`/product/${order.productId}`" class="text-sm text-gray-800 hover:text-primary">
                    {{ order.productName }}
                  </NuxtLink>
                  <p class="text-xs text-gray-400 mt-1">{{ order.specs }}</p>
                </div>
                <div class="text-right">
                  <div class="text-sm font-bold text-primary">¥{{ order.price.toFixed(2) }}</div>
                  <div class="text-xs text-gray-400">x{{ order.quantity }}</div>
                </div>
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
                    v-if="order.status === 3"
                    class="px-4 py-1.5 border border-primary text-primary text-sm rounded hover:bg-primary-50 transition-colors"
                    @click="handleReview(order)"
                  >
                    去评价
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
const currentPage = ref(1)
const totalPages = ref(1)

const tabs = ref([
  { label: '全部订单', value: 'all', count: 0 },
  { label: '待付款', value: '0', count: 0 },
  { label: '待发货', value: '1', count: 0 },
  { label: '待收货', value: '2', count: 0 },
  { label: '已完成', value: '3', count: 0 },
])

const statusTextMap: Record<number, string> = {
  0: '待付款',
  1: '待发货',
  2: '待收货',
  3: '已完成',
  4: '已取消',
  5: '退款中',
  6: '已退款'
}

const statusClassMap: Record<number, string> = {
  0: 'text-red-500',
  1: 'text-orange-500',
  2: 'text-blue-500',
  3: 'text-green-500',
  4: 'text-gray-500',
  5: 'text-yellow-500',
  6: 'text-gray-400'
}

// 订单列表从接口获取
const orders = ref<any[]>([])

const fetchOrders = async () => {
  try {
    const data = await get('/order/my-orders', {
      pageNum: currentPage.value,
      pageSize: 10,
      status: activeTab.value === 'all' ? undefined : activeTab.value
    })
    orders.value = (data?.list || []).map((o: any) => ({
      id: o.id,
      orderNo: o.orderNo,
      date: o.createdAt,
      createdAt: o.createdAt,
      status: o.status,
      statusText: statusTextMap[o.status] || '未知',
      statusClass: statusClassMap[o.status] || 'text-gray-500',
      productId: o.items?.[0]?.productId || o.productId,
      image: o.items?.[0]?.productImage || o.productImage,
      productName: o.items?.[0]?.productName || o.productName,
      specs: o.items?.[0]?.skuName || '',
      price: o.items?.[0]?.price || o.totalAmount,
      quantity: o.items?.[0]?.quantity || 1,
      total: o.payAmount || o.totalAmount
    }))
    totalPages.value = Math.ceil((data?.total || 0) / 10)
    
    // 更新 tab 计数
    tabs.value.forEach(tab => {
      if (tab.value !== 'all') {
        tab.count = orders.value.filter(o => o.status === parseInt(tab.value)).length
      }
    })
  } catch (e) {
    console.error('获取订单列表失败:', e)
    orders.value = []
  }
}

const filteredOrders = computed(() => {
  if (activeTab.value === 'all') return orders.value
  return orders.value.filter(o => o.status === parseInt(activeTab.value))
})

function switchTab(tab: string) {
  activeTab.value = tab
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
  navigateTo(`/user/reviews?orderNo=${order.orderNo || order.id}`)
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
