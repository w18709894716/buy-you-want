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
        <h2 class="text-xl font-bold text-gray-800 mb-4">我的优惠券</h2>

        <!-- Tab 栏 -->
        <div class="bg-white rounded-t-lg border-b flex">
          <button
            v-for="tab in tabs"
            :key="tab.value"
            :class="[
              'px-6 py-3 text-sm font-medium border-b-2 transition-colors',
              activeTab === tab.value ? 'border-primary text-primary' : 'border-transparent text-gray-600 hover:text-gray-800'
            ]"
            @click="activeTab = tab.value"
          >
            {{ tab.label }}
          </button>
        </div>

        <!-- 优惠券列表 -->
        <div v-if="filteredCoupons.length" class="bg-white rounded-b-lg p-4 space-y-3">
          <div
            v-for="coupon in filteredCoupons"
            :key="coupon.recordId"
            class="flex border rounded-lg overflow-hidden hover:shadow-md transition-shadow"
          >
            <!-- 左侧金额区 -->
            <div class="w-28 flex-shrink-0 flex flex-col items-center justify-center bg-primary-50 border-r border-dashed">
              <template v-if="coupon.type === 2">
                <span class="text-2xl font-bold text-primary">{{ coupon.discountValue }}</span>
                <span class="text-xs text-gray-500">折</span>
              </template>
              <template v-else>
                <span class="text-xl font-bold text-primary">¥{{ coupon.discountValue }}</span>
              </template>
              <span class="text-xs text-gray-500 mt-1">
                {{ coupon.type === 1 ? `满${coupon.minAmount}可用` : coupon.type === 3 ? '无门槛' : '' }}
              </span>
            </div>
            <!-- 右侧信息区 -->
            <div class="flex-1 p-3 flex flex-col justify-between">
              <div>
                <p class="text-sm font-medium text-gray-800">{{ coupon.name }}</p>
                <p class="text-xs text-gray-400 mt-1">
                  有效期：{{ formatDate(coupon.startTime) }} ~ {{ formatDate(coupon.endTime) }}
                </p>
              </div>
              <div class="flex items-center justify-between mt-2">
                <span v-if="isExpired(coupon.endTime)" class="text-xs text-red-400">已过期</span>
                <span v-else-if="activeTab === 'used'" class="text-xs text-gray-400">已使用</span>
                <span v-else class="text-xs text-primary">可使用</span>
              </div>
            </div>
          </div>
        </div>
        <div v-else class="bg-white rounded-b-lg p-12 text-center text-gray-400">
          <p class="text-4xl mb-3">🎟️</p>
          <p>{{ activeTab === 'available' ? '暂无可用优惠券' : activeTab === 'used' ? '暂无已使用优惠券' : '暂无已过期优惠券' }}</p>
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
  { label: '可使用', value: 'available' },
  { label: '已使用', value: 'used' },
  { label: '已过期', value: 'expired' },
]

const activeTab = ref('available')
const coupons = ref<any[]>([])

const filteredCoupons = computed(() => {
  if (activeTab.value === 'available') {
    return coupons.value.filter(c => !isExpired(c.endTime))
  }
  if (activeTab.value === 'expired') {
    return coupons.value.filter(c => isExpired(c.endTime))
  }
  // used - 暂时返回空（后端暂无已使用状态字段）
  return []
})

function isExpired(endTime: string): boolean {
  return new Date(endTime) < new Date()
}

function formatDate(dt: string): string {
  if (!dt) return ''
  return dt.substring(0, 10)
}

async function fetchCoupons() {
  try {
    coupons.value = await get<any[]>('/coupon/my-coupons', { status: 0 })
  } catch (e) {
    console.error(e)
  }
}

onMounted(() => {
  userStore.getUserInfo()
  fetchCoupons()
})
</script>
