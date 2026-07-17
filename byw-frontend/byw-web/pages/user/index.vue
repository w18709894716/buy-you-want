<template>
  <div class="max-w-7xl mx-auto px-4 py-6">
    <div class="flex gap-6">
      <!-- 侧边栏 -->
      <aside class="w-52 flex-shrink-0 hidden md:block">
        <!-- 用户信息卡片 -->
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

        <!-- 导航菜单 -->
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
        <!-- 概览卡片 -->
        <div class="grid grid-cols-2 sm:grid-cols-5 gap-4 mb-6">
          <NuxtLink
            v-for="stat in statsCards"
            :key="stat.label"
            :to="stat.path"
            class="bg-white rounded-lg p-4 text-center hover:shadow-md transition-shadow cursor-pointer"
          >
            <div class="text-2xl mb-1">{{ stat.icon }}</div>
            <div class="text-2xl font-bold text-gray-800">{{ stat.value }}</div>
            <div class="text-xs text-gray-500 mt-1">{{ stat.label }}</div>
          </NuxtLink>
        </div>

        <!-- 最近订单 -->
        <div class="bg-white rounded-lg p-6">
          <div class="flex items-center justify-between mb-4">
            <h3 class="font-medium text-gray-800">最近订单</h3>
            <NuxtLink to="/user/orders" class="text-sm text-primary hover:text-primary-600">查看全部 &gt;</NuxtLink>
          </div>
          <div class="space-y-4">
            <div v-for="order in recentOrders" :key="order.id" class="border rounded-lg p-4">
              <div class="flex flex-wrap items-center justify-between text-sm text-gray-500 mb-3 gap-1">
                <span>订单号：{{ order.id }}</span>
                <div class="flex items-center gap-2">
                  <span>{{ order.date }}</span>
                  <span :class="order.statusClass">{{ order.statusText }}</span>
                </div>
              </div>
              <div class="flex items-center gap-4">
                <img :src="order.image" :alt="order.productName" class="w-16 h-16 object-cover rounded" />
                <div class="flex-1">
                  <p class="text-sm text-gray-800">{{ order.productName }}</p>
                  <p class="text-xs text-gray-400 mt-0.5">{{ order.specs }}</p>
                </div>
                <div class="text-right">
                  <div class="text-sm font-bold text-primary">¥{{ order.total.toFixed(2) }}</div>
                  <div class="text-xs text-gray-400">x{{ order.quantity }}</div>
                </div>
              </div>
            </div>
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
const route = useRoute()

const sidebarMenu = [
  { icon: '👤', label: '个人中心', path: '/user' },
  { icon: '📦', label: '我的订单', path: '/user/orders' },
  { icon: '📍', label: '收货地址', path: '/user/address' },
  { icon: '🎟️', label: '我的优惠券', path: '/user/coupons' },
  { icon: '⭐', label: '我的评价', path: '/user/reviews' },
  { icon: '❤️', label: '我的收藏', path: '/user/favorites' },
]

// 概览卡片数据（从接口获取）
const statsCards = ref([
  { icon: '💰', label: '待付款', value: 0, path: '/user/orders?status=0' },
  { icon: '📋', label: '待发货', value: 0, path: '/user/orders?status=1' },
  { icon: '🚚', label: '待收货', value: 0, path: '/user/orders?status=2' },
  { icon: '⭐', label: '待评价', value: 0, path: '/user/orders?status=3' },
  { icon: '🎟️', label: '优惠券', value: 0, path: '/user/coupons' },
])

// 获取订单各状态数量
async function fetchOrderCounts() {
  try {
    const counts = await get<Record<number, number>>('/order/status-counts')
    if (counts) {
      statsCards.value[0].value = counts[0] || 0 // 待付款
      statsCards.value[1].value = counts[1] || 0 // 待发货
      statsCards.value[2].value = counts[2] || 0 // 待收货
      statsCards.value[3].value = counts[3] || 0 // 待评价
    }
  } catch (e) {
    console.error('获取订单统计失败', e)
  }
}

// 获取优惠券数量
async function fetchCouponCount() {
  try {
    const coupons = await get<any[]>('/coupon/my-coupons?status=0')
    if (coupons) {
      statsCards.value[4].value = coupons.length
    }
  } catch (e) {
    console.error('获取优惠券统计失败', e)
  }
}

const recentOrders = [
  {
    id: 'BYW202606160001',
    date: '2026-06-15',
    statusText: '待发货',
    statusClass: 'text-orange-500',
    image: 'https://via.placeholder.com/60x60?text=iPhone',
    productName: 'Apple iPhone 15 Pro Max 256GB',
    specs: '原色钛金属 / 256GB',
    total: 9999,
    quantity: 1,
  },
  {
    id: 'BYW202606160002',
    date: '2026-06-14',
    statusText: '待收货',
    statusClass: 'text-blue-500',
    image: 'https://via.placeholder.com/60x60?text=Sony',
    productName: 'Sony WH-1000XM5 无线降噪耳机',
    specs: '黑色',
    total: 2299,
    quantity: 1,
  },
]

onMounted(() => {
  userStore.getUserInfo()
  fetchOrderCounts()
  fetchCouponCount()
})
</script>
