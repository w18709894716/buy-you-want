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
                  <span v-if="order.shopName" class="flex items-center gap-1 text-gray-700 font-medium">
                    <svg class="w-4 h-4 text-primary" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 3h2l.4 2M7 13h10l4-8H5.4M7 13L5.4 5M7 13l-2.293 2.293c-.63.63-.184 1.707.707 1.707H17m0 0a2 2 0 100 4 2 2 0 000-4zm-8 2a2 2 0 11-4 0 2 2 0 014 0z"/></svg>
                    {{ order.shopName }}
                  </span>
                  <span>订单号：{{ order.orderNo || order.id }}</span>
                  <span>{{ order.date }}</span>
                  <button
                    class="text-orange-500 hover:text-orange-600 hover:underline font-medium"
                    @click="viewOrderDetail(order)"
                  >订单详情</button>
                </div>
                <span :class="order.statusClass" class="font-medium">{{ order.statusText }}</span>
              </div>

              <!-- 订单商品（支持多件，售后/加购/物流操作均为商品级） -->
              <div class="space-y-3 mb-3">
                <div
                  v-for="(item, idx) in order.items"
                  :key="item.id || item.skuId || idx"
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
                    <p v-if="item.afterSaleId" class="text-xs mt-1" :class="afterSaleStatusClass(item.afterSaleStatus)">
                      {{ afterSaleStatusText(item.afterSaleStatus, item.afterSaleType) }}
                    </p>
                  </div>
                  <div class="text-right">
                    <div class="text-sm font-bold text-primary">¥{{ (item.price || 0).toFixed(2) }}</div>
                    <div class="text-xs text-gray-400">x{{ item.quantity }}</div>
                  </div>
                  <!-- 商品级操作 -->
                  <div class="flex flex-col items-stretch gap-1.5 w-24 flex-shrink-0">
                    <button
                      v-if="canApplyAfterSale(order, item)"
                      class="px-2 py-1 text-xs border border-orange-300 text-orange-500 rounded hover:bg-orange-50 transition-colors"
                      @click="openAfterSaleDialog(order, item)"
                    >申请售后</button>
                    <button
                      v-if="item.afterSaleId"
                      class="px-2 py-1 text-xs border border-primary text-primary rounded hover:bg-primary-50 transition-colors"
                      @click="openRefundDetail(order, item)"
                    >退款明细</button>
                    <button
                      class="px-2 py-1 text-xs border border-gray-300 text-gray-600 rounded hover:bg-gray-50 transition-colors disabled:opacity-60"
                      :disabled="addingCartItemKey === (item.id || item.skuId)"
                      @click="handleAddToCart(item)"
                    >{{ addingCartItemKey === (item.id || item.skuId) ? '加入中...' : '加入购物车' }}</button>
                    <!-- 查看物流：该商品已发货且订单未完成，悬浮展示对应包裹 -->
                    <div
                      v-if="item.shipStatus === 1 && (order.status === 2 || order.status === 7)"
                      class="relative"
                      @mouseenter="showLogistics(order, item)"
                      @mouseleave="hideLogistics"
                    >
                      <button class="w-full px-2 py-1 text-xs border border-gray-300 text-gray-600 rounded hover:bg-gray-50 transition-colors">
                        查看物流
                      </button>
                      <!-- 悬浮物流面板 -->
                      <div
                        v-if="logisticsHover.key === logisticsKey(order, item)"
                        class="absolute right-0 top-full mt-2 w-80 bg-white rounded-lg shadow-xl border border-gray-100 p-4 z-30 text-left"
                      >
                        <div v-if="logisticsHover.loading" class="py-6 text-center text-sm text-gray-400">物流信息加载中...</div>
                        <template v-else-if="logisticsHover.packages.length">
                          <div v-for="(pkg, pIdx) in logisticsHover.packages" :key="pkg.id || pIdx" class="mb-3 last:mb-0">
                            <div class="flex items-center justify-between mb-1">
                              <span class="text-sm font-medium text-gray-800 truncate">
                                <span v-if="logisticsHover.packages.length > 1" class="text-gray-400">包裹{{ pIdx + 1 }} · </span>{{ pkg.companyName || '快递运输中' }}
                              </span>
                              <span class="text-xs px-2 py-0.5 rounded flex-shrink-0" :class="logisticsStatusClass(pkg.status)">
                                {{ logisticsStatusText(pkg.status) }}
                              </span>
                            </div>
                            <p class="text-xs text-gray-400 mb-2">运单号：<span class="font-mono">{{ pkg.trackingNo || '-' }}</span></p>
                            <div v-if="pkg.latestTraces && pkg.latestTraces.length" class="space-y-1.5">
                              <div v-for="(trace, tIdx) in pkg.latestTraces" :key="tIdx" class="text-xs">
                                <p :class="tIdx === 0 ? 'text-gray-700 font-medium' : 'text-gray-400'">{{ trace.description }}</p>
                                <p class="text-gray-300">{{ formatTraceTime(trace.traceTime) }}<span v-if="trace.location" class="ml-2">{{ trace.location }}</span></p>
                              </div>
                            </div>
                            <p v-else class="text-xs text-gray-400">暂无物流轨迹</p>
                          </div>
                          <NuxtLink
                            :to="`/logistics?orderNo=${order.orderNo}${item.trackingNo ? '&trackingNo=' + encodeURIComponent(item.trackingNo) : ''}`"
                            class="block mt-2 pt-2 border-t border-gray-100 text-xs text-primary text-center hover:underline"
                          >查看完整物流轨迹 →</NuxtLink>
                        </template>
                        <div v-else class="py-6 text-center text-sm text-gray-400">暂无物流信息</div>
                      </div>
                    </div>
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
                <p v-if="reviewSummaries[order.orderNo].merchantReply" class="text-xs text-gray-500 mt-1 line-clamp-2">
                  <span class="text-orange-500">商家回复：</span>{{ reviewSummaries[order.orderNo].merchantReply }}
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

        <!-- 加载更多触发器 & 状态 -->
        <div ref="loadMoreTrigger" class="h-px"></div>
        <div v-if="loadingMore" class="mt-4 py-2 text-center text-sm text-gray-400">加载中...</div>
        <div v-else-if="orders.length > 0 && !hasMore" class="mt-4 py-2 text-center text-xs text-gray-300">没有更多了</div>
      </div>
    </div>

    <!-- 确认弹窗 -->
    <Teleport to="body">
      <Transition name="modal">
        <div v-if="confirmDialog" class="fixed inset-0 z-[60] flex items-center justify-center p-4">
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
    <!-- 申请售后弹窗（商品级） -->
    <AfterSaleModal
      v-model="afterSaleModal.visible"
      :order="afterSaleModal.order"
      :item="afterSaleModal.item"
      @toast="(t) => showToast(t.message, t.type)"
      @submitted="fetchOrders()"
    />

    <!-- Toast 通知 -->
    <Teleport to="body">
      <Transition name="toast">
        <div
          v-if="toast.visible"
          class="fixed top-20 left-1/2 -translate-x-1/2 z-[70] px-5 py-2.5 rounded-lg shadow-lg text-sm flex items-center gap-2"
          :class="toast.type === 'success' ? 'bg-green-500 text-white' : 'bg-red-500 text-white'"
        >
          <span>{{ toast.type === 'success' ? '✓' : '✕' }}</span>
          <span>{{ toast.message }}</span>
        </div>
      </Transition>
    </Teleport>

    <!-- 退款明细弹窗 -->
    <RefundDetailModal
      v-model="refundDetailVisible"
      :order-no="refundDetailOrderNo"
      :item-id="refundDetailItemId"
      @toast="(t) => showToast(t.message, t.type)"
      @refreshed="fetchOrders()"
    />
  </div>
</template>

<script setup lang="ts">
import { useUserStore } from '~/stores/user'
import { useCartStore } from '~/stores/cart'
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
const pageSize = 10
const total = ref(0)
const loadingMore = ref(false)
const loadMoreTrigger = ref<HTMLElement | null>(null)
let observer: IntersectionObserver | null = null

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
  { label: '退款中', value: '5', count: 0 },
  { label: '交易关闭', value: '4', count: 0 },
])

const statusTextMap: Record<number, string> = {
  0: '待付款',
  1: '待发货',
  2: '待收货',
  3: '交易完成',
  4: '交易关闭',
  5: '退款中',
  7: '部分发货'
}

const statusClassMap: Record<number, string> = {
  0: 'text-red-500',
  1: 'text-orange-500',
  2: 'text-blue-500',
  3: 'text-green-500',
  4: 'text-gray-500',
  5: 'text-yellow-500',
  7: 'text-orange-500'
}

// 订单列表从接口获取
const orders = ref<any[]>([])
// 已评价订单的评价摘要：orderNo -> { rating, content, appendContent }
const reviewSummaries = ref<Record<string, any>>({})

// 将后端订单数据映射为列表展示结构
function mapOrder(o: any) {
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
    shopName: o.shopName,
    date: o.createdAt,
    createdAt: o.createdAt,
    status: o.status,
    reviewed: o.reviewed,
    closeType: o.closeType,
    afterSaleId: o.afterSaleId,
    afterSaleStatus: o.afterSaleStatus,
    afterSaleType: o.afterSaleType,
    statusText: statusTextMap[o.status] || '未知',
    statusClass: statusClassMap[o.status] || 'text-gray-500',
    items,
    // 保留首件商品用于评价等跳转场景
    productId: items[0]?.productId,
    productName: items[0]?.productName,
    quantity: totalQuantity,
    total: o.payAmount || o.totalAmount
  }
}

// 是否还有更多可加载
const hasMore = computed(() => orders.value.length < total.value)

const fetchOrders = async (append = false) => {
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
      pageSize,
      status: statusParam,
      reviewed: reviewedParam
    })
    const mapped = (data?.list || []).map(mapOrder)
    orders.value = append ? [...orders.value, ...mapped] : mapped
    total.value = data?.total || 0
    // 已评价子筛选：拉取评价摘要用于卡片展示
    if (activeTab.value === 'review' && reviewSubTab.value === 'reviewed') {
      fetchReviewSummaries()
    } else {
      reviewSummaries.value = {}
    }
  } catch (e) {
    console.error('获取订单列表失败:', e)
    if (!append) orders.value = []
  }
}

// 滚动到底部时加载下一页
async function loadMore() {
  if (loadingMore.value || !hasMore.value) return
  loadingMore.value = true
  currentPage.value++
  try {
    await fetchOrders(true)
  } finally {
    loadingMore.value = false
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
          appendContent: first.appendContent || '',
          merchantReply: first.merchantReply || ''
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
      // 按 tab value 映射计数，避免依赖数组下标；“评价”tab 对应待评价(3)
      const countMap: Record<string, number> = {
        '0': counts[0] || 0,
        '1': counts[1] || 0,
        '2': counts[2] || 0,
        'review': counts[3] || 0,
        '5': counts[5] || 0,
        '4': counts[4] || 0
      }
      tabs.value.forEach(tab => {
        if (tab.value in countMap) tab.count = countMap[tab.value]
      })
      // “全部订单” 不展示数量，订单过多时依靠下拉加载查看
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
  // 观察底部触发器，进入视口即加载下一页
  observer = new IntersectionObserver((entries) => {
    if (entries[0]?.isIntersecting) loadMore()
  }, { rootMargin: '200px' })
  if (loadMoreTrigger.value) observer.observe(loadMoreTrigger.value)
})

onUnmounted(() => {
  if (countdownTimer) clearInterval(countdownTimer)
  if (observer) observer.disconnect()
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

// ===== Toast 通知 =====
const toast = reactive({ visible: false, message: '', type: 'success' as 'success' | 'error' })
let toastTimer: ReturnType<typeof setTimeout> | null = null
function showToast(message: string, type: 'success' | 'error' = 'success') {
  if (toastTimer) clearTimeout(toastTimer)
  toast.visible = true
  toast.message = message
  toast.type = type
  toastTimer = setTimeout(() => { toast.visible = false }, 2500)
}

// ===== 加入购物车（商品级，单品再次购买） =====
const cartStore = useCartStore()
const addingCartItemKey = ref<any>(null)

async function handleAddToCart(item: any) {
  if (addingCartItemKey.value) return
  if (!item.skuId) {
    showToast('该商品缺少SKU信息，无法加入购物车', 'error')
    return
  }
  addingCartItemKey.value = item.id || item.skuId
  try {
    await post('/cart/add', null, { params: { skuId: item.skuId, quantity: item.quantity || 1 } })
    await cartStore.getCartList()
    showToast('已加入购物车')
  } catch (e: any) {
    showToast(e?.message || '加入购物车失败', 'error')
  } finally {
    addingCartItemKey.value = null
  }
}

// ===== 申请售后（商品级，弹窗抽取为 AfterSaleModal 组件） =====
const afterSaleModal = reactive({ visible: false, order: null as any, item: null as any })

function openAfterSaleDialog(order: any, item: any) {
  afterSaleModal.order = order
  afterSaleModal.item = item
  afterSaleModal.visible = true
}

/** 该商品是否可申请售后：待发货/待收货/部分发货/交易完成/退款中，且该商品无进行中或已完成的退款类售后 */
function canApplyAfterSale(order: any, item: any) {
  if (![1, 2, 3, 5, 7].includes(order.status)) return false
  if (!item.afterSaleId) return true
  if ([0, 1, 5, 6].includes(item.afterSaleStatus)) return false // 售后进行中
  if (item.afterSaleStatus === 3) return false // 已完成退款
  return true // 已拒绝(2)/已撤销(4)可重新申请
}

/** 商品行售后状态标签 */
const afterSaleStatusText = (s: number, t?: number) => {
  if (s === 3) return t === 2 ? '退货退款完成' : '退款完成'
  return ({
    0: '售后待审核', 1: '售后待寄回', 2: '售后已拒绝',
    4: '售后已撤销', 5: '待商家收货', 6: '退款中'
  } as Record<number, string>)[s] || ''
}

const afterSaleStatusClass = (s: number) =>
  ({
    0: 'text-orange-500', 1: 'text-orange-500', 2: 'text-red-400',
    3: 'text-green-500', 4: 'text-gray-400', 5: 'text-orange-500', 6: 'text-yellow-500'
  } as Record<number, string>)[s] || 'text-gray-400'

const refundDetailVisible = ref(false)
const refundDetailOrderNo = ref('')
const refundDetailItemId = ref<number | null>(null)
/** 打开退款明细：传 item 为商品级售后，不传为历史订单级售后 */
function openRefundDetail(order: any, item?: any) {
  refundDetailOrderNo.value = order.orderNo
  refundDetailItemId.value = item?.id ?? null
  refundDetailVisible.value = true
}

// ===== 查看物流（商品级悬浮展示） =====
const logisticsHover = reactive({ key: '', loading: false, packages: [] as any[] })
// 物流缓存仍按订单号存 track-all 全量包裹，展示时按商品运单号过滤
const logisticsCache: Record<string, any[]> = {}
let hideLogisticsTimer: ReturnType<typeof setTimeout> | null = null

// 悬浮面板 key 必须用商品行唯一标识：同包裹多商品共用运单号，若用运单号会导致多行同时弹出重叠面板
function logisticsKey(order: any, item: any) {
  return `${order.orderNo}#${item.id ?? item.skuId ?? item.trackingNo}`
}

/** 按商品运单号过滤包裹，无匹配时兜底展示订单全部包裹；同运单号多条记录时去重 */
function filterPackages(packages: any[], item: any) {
  const dedup = (list: any[]) => {
    const seen = new Set<string>()
    return list.filter(p => {
      const k = p.trackingNo || String(p.id)
      if (seen.has(k)) return false
      seen.add(k)
      return true
    })
  }
  if (!item.trackingNo) return dedup(packages)
  const matched = packages.filter(p => p.trackingNo === item.trackingNo)
  return dedup(matched.length ? matched : packages)
}

async function showLogistics(order: any, item: any) {
  if (hideLogisticsTimer) { clearTimeout(hideLogisticsTimer); hideLogisticsTimer = null }
  const key = logisticsKey(order, item)
  logisticsHover.key = key
  if (logisticsCache[order.orderNo]) {
    logisticsHover.packages = filterPackages(logisticsCache[order.orderNo], item)
    logisticsHover.loading = false
    return
  }
  logisticsHover.loading = true
  logisticsHover.packages = []
  try {
    const data = await get<any[]>(`/logistics/track-all/${encodeURIComponent(order.orderNo)}`)
    const packages = (data || []).map((pkg: any) => ({
      ...pkg,
      // 轨迹倒序取最新3条用于悬浮简览
      latestTraces: (pkg.traces || []).slice().reverse().slice(0, 3)
    }))
    logisticsCache[order.orderNo] = packages
    if (logisticsHover.key === key) logisticsHover.packages = filterPackages(packages, item)
  } catch (e) {
    if (logisticsHover.key === key) logisticsHover.packages = []
  } finally {
    if (logisticsHover.key === key) logisticsHover.loading = false
  }
}

function hideLogistics() {
  // 延迟收起，避免鼠标在按钮与面板间移动时闪烁
  hideLogisticsTimer = setTimeout(() => { logisticsHover.key = '' }, 200)
}

const logisticsStatusText = (s: number) =>
  ({ 0: '已揽收', 1: '运输中', 2: '派送中', 3: '已签收', 4: '异常' } as Record<number, string>)[s] || '未知'

const logisticsStatusClass = (s: number) =>
  ({
    0: 'bg-blue-50 text-blue-600',
    1: 'bg-amber-50 text-amber-600',
    2: 'bg-purple-50 text-purple-600',
    3: 'bg-green-50 text-green-600',
    4: 'bg-red-50 text-red-600',
  } as Record<number, string>)[s] || 'bg-gray-50 text-gray-600'

const formatTraceTime = (t: string) => (t ? t.replace('T', ' ').substring(0, 16) : '')
</script>

<style scoped>
.modal-enter-active, .modal-leave-active { transition: all 0.3s ease; }
.modal-enter-from, .modal-leave-to { opacity: 0; }
.toast-enter-active, .toast-leave-active { transition: all 0.3s ease; }
.toast-enter-from, .toast-leave-to { opacity: 0; transform: translate(-50%, -10px); }
</style>
