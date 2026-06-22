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
        <div class="grid grid-cols-2 sm:grid-cols-4 gap-4 mb-6">
          <div
            v-for="stat in statsCards"
            :key="stat.label"
            class="bg-white rounded-lg p-4 text-center hover:shadow-md transition-shadow cursor-pointer"
          >
            <div class="text-2xl mb-1">{{ stat.icon }}</div>
            <div class="text-2xl font-bold text-gray-800">{{ stat.value }}</div>
            <div class="text-xs text-gray-500 mt-1">{{ stat.label }}</div>
          </div>
        </div>

        <!-- 订单快捷入口 -->
        <div class="bg-white rounded-lg p-6 mb-6">
          <h3 class="font-medium text-gray-800 mb-4">我的订单</h3>
          <div class="grid grid-cols-5 gap-4">
            <NuxtLink
              v-for="orderType in orderTypes"
              :key="orderType.label"
              :to="`/user/orders?status=${orderType.status}`"
              class="flex flex-col items-center gap-2 p-3 rounded-lg hover:bg-gray-50 transition-colors"
            >
              <div class="relative">
                <span class="text-3xl">{{ orderType.icon }}</span>
                <span
                  v-if="orderType.badge"
                  class="absolute -top-1 -right-2 bg-primary text-white text-xs rounded-full h-4 min-w-4 flex items-center justify-center px-1"
                >
                  {{ orderType.badge }}
                </span>
              </div>
              <span class="text-xs text-gray-600">{{ orderType.label }}</span>
            </NuxtLink>
          </div>
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

const statsCards = [
  { icon: '📦', label: '待收货', value: 3 },
  { icon: '💰', label: '待付款', value: 2 },
  { icon: '⭐', label: '待评价', value: 5 },
  { icon: '🎟️', label: '优惠券', value: 8 },
]

const orderTypes = [
  { icon: '💳', label: '待付款', status: 'pending_pay', badge: 2 },
  { icon: '📋', label: '待发货', status: 'pending_ship', badge: 1 },
  { icon: '🚚', label: '待收货', status: 'pending_receive', badge: 3 },
  { icon: '⭐', label: '待评价', status: 'pending_review', badge: 5 },
  { icon: '✅', label: '已完成', status: 'completed', badge: 0 },
]

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
})
</script>
