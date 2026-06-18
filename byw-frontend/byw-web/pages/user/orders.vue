<template>
  <div class="max-w-7xl mx-auto px-4 py-6">
    <div class="flex gap-6">
      <!-- 侧边栏 -->
      <aside class="w-52 flex-shrink-0">
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
              <div class="flex items-center justify-between text-sm text-gray-500 mb-3 pb-3 border-b border-gray-100">
                <div class="flex items-center gap-4">
                  <span>订单号：{{ order.id }}</span>
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
              <div class="flex items-center justify-between">
                <div class="text-sm text-gray-500">
                  共 {{ order.quantity }} 件商品，合计：
                  <span class="text-primary font-bold text-lg">¥{{ order.total.toFixed(2) }}</span>
                </div>
                <div class="flex gap-2">
                  <button
                    v-if="order.status === 'pending_pay'"
                    class="px-4 py-1.5 bg-primary text-white text-sm rounded hover:bg-primary-600 transition-colors"
                  >
                    立即付款
                  </button>
                  <button
                    v-if="order.status === 'pending_receive'"
                    class="px-4 py-1.5 bg-primary text-white text-sm rounded hover:bg-primary-600 transition-colors"
                  >
                    确认收货
                  </button>
                  <button
                    v-if="order.status === 'completed'"
                    class="px-4 py-1.5 border border-primary text-primary text-sm rounded hover:bg-primary-50 transition-colors"
                  >
                    去评价
                  </button>
                  <button
                    class="px-4 py-1.5 border border-gray-300 text-gray-600 text-sm rounded hover:bg-gray-50 transition-colors"
                  >
                    查看详情
                  </button>
                  <button
                    v-if="order.status === 'pending_pay'"
                    class="px-4 py-1.5 border border-gray-300 text-gray-500 text-sm rounded hover:bg-gray-50 transition-colors"
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
  </div>
</template>

<script setup lang="ts">
import { useUserStore } from '~/stores/user'

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
const totalPages = ref(3)

const tabs = [
  { label: '全部订单', value: 'all', count: 15 },
  { label: '待付款', value: 'pending_pay', count: 2 },
  { label: '待发货', value: 'pending_ship', count: 1 },
  { label: '待收货', value: 'pending_receive', count: 3 },
  { label: '已完成', value: 'completed', count: 9 },
]

// 占位订单数据
const orders = ref([
  {
    id: 'BYW202606160001',
    date: '2026-06-15 14:30:22',
    status: 'pending_ship',
    statusText: '待发货',
    statusClass: 'text-orange-500',
    productId: 1,
    image: 'https://via.placeholder.com/80x80?text=iPhone',
    productName: 'Apple iPhone 15 Pro Max 256GB 原色钛金属',
    specs: '原色钛金属 / 256GB',
    price: 9999,
    quantity: 1,
    total: 9999,
  },
  {
    id: 'BYW202606140002',
    date: '2026-06-14 10:15:33',
    status: 'pending_receive',
    statusText: '待收货',
    statusClass: 'text-blue-500',
    productId: 4,
    image: 'https://via.placeholder.com/80x80?text=Sony',
    productName: 'Sony WH-1000XM5 无线降噪头戴式耳机',
    specs: '黑色',
    price: 2299,
    quantity: 1,
    total: 2299,
  },
  {
    id: 'BYW202606120003',
    date: '2026-06-12 09:45:11',
    status: 'pending_pay',
    statusText: '待付款',
    statusClass: 'text-red-500',
    productId: 6,
    image: 'https://via.placeholder.com/80x80?text=Dyson',
    productName: '戴森 V15 Detect 无线吸尘器',
    specs: '金色',
    price: 4990,
    quantity: 1,
    total: 4990,
  },
  {
    id: 'BYW202606100004',
    date: '2026-06-10 16:22:08',
    status: 'completed',
    statusText: '已完成',
    statusClass: 'text-green-500',
    productId: 7,
    image: 'https://via.placeholder.com/80x80?text=LEGO',
    productName: 'LEGO 乐高 42151 布加迪 Bolide',
    specs: '标准版',
    price: 399,
    quantity: 2,
    total: 798,
  },
  {
    id: 'BYW202606080005',
    date: '2026-06-08 11:30:45',
    status: 'pending_receive',
    statusText: '待收货',
    statusClass: 'text-blue-500',
    productId: 2,
    image: 'https://via.placeholder.com/80x80?text=Huawei',
    productName: '华为 Mate 60 Pro 512GB 雅丹黑',
    specs: '雅丹黑 / 512GB',
    price: 7999,
    quantity: 1,
    total: 7999,
  },
  {
    id: 'BYW202606050006',
    date: '2026-06-05 20:18:30',
    status: 'completed',
    statusText: '已完成',
    statusClass: 'text-green-500',
    productId: 10,
    image: 'https://via.placeholder.com/80x80?text=Bose',
    productName: 'Bose QuietComfort 消噪耳塞 II',
    specs: '黑色',
    price: 1699,
    quantity: 1,
    total: 1699,
  },
])

const filteredOrders = computed(() => {
  if (activeTab.value === 'all') return orders.value
  return orders.value.filter(o => o.status === activeTab.value)
})

function switchTab(tab: string) {
  activeTab.value = tab
  currentPage.value = 1
}

onMounted(() => {
  userStore.getUserInfo()
})
</script>
