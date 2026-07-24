<template>
  <div class="min-h-screen bg-gray-50">
    <!-- 顶部标题栏 -->
    <div class="bg-gradient-to-r from-primary-500 to-primary-700 py-6">
      <div class="max-w-5xl mx-auto px-4">
        <h1 class="text-2xl font-bold text-white flex items-center gap-2">
          <span>{{ isNewScene ? '🎁' : '💰' }}</span> {{ pageTitle }}
        </h1>
        <p class="text-white/80 text-sm mt-1">{{ pageSubtitle }}</p>
      </div>
    </div>

    <!-- 优惠券列表 -->
    <div class="max-w-5xl mx-auto px-4 py-6">
      <!-- 加载中 -->
      <div v-if="loading" class="flex items-center justify-center py-20 text-gray-400">
        <span class="animate-spin mr-2">⟳</span> 加载中...
      </div>

      <!-- 空状态 -->
      <div v-else-if="!coupons.length" class="text-center py-20 text-gray-400">
        <p class="text-5xl mb-4">🎟️</p>
        <p class="text-lg">{{ isNewScene ? '暂无新人专享券' : '暂无可领优惠券' }}</p>
        <p class="text-sm mt-2">稍后再来看看吧</p>
      </div>

      <!-- 列表 -->
      <div v-else class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
        <div
          v-for="coupon in coupons"
          :key="coupon.id"
          class="bg-white rounded-lg overflow-hidden shadow-sm hover:shadow-md transition-shadow flex"
        >
          <!-- 左侧金额区 -->
          <div class="w-28 flex-shrink-0 flex flex-col items-center justify-center bg-primary-50 border-r border-dashed border-primary-200 p-3">
            <template v-if="coupon.type === 2">
              <span class="text-3xl font-bold text-primary">{{ coupon.discountValue }}</span>
              <span class="text-xs text-gray-500 mt-1">折</span>
            </template>
            <template v-else>
              <span class="text-3xl font-bold text-primary">¥{{ coupon.discountValue }}</span>
            </template>
            <span class="text-xs text-gray-500 mt-1 text-center">
              {{ minAmountText(coupon) }}
            </span>
          </div>

          <!-- 右侧信息区 -->
          <div class="flex-1 p-3 flex flex-col justify-between min-w-0">
            <div>
              <div class="flex items-center gap-2">
                <span
                  class="text-xs px-1.5 py-0.5 rounded"
                  :class="typeClass(coupon.type)"
                >
                  {{ typeText(coupon.type) }}
                </span>
                <h3 class="font-medium text-gray-800 text-sm truncate">{{ coupon.name }}</h3>
              </div>
              <p class="text-xs text-gray-400 mt-2">
                有效期：{{ formatDate(coupon.startTime) }} ~ {{ formatDate(coupon.endTime) }}
              </p>
              <p class="text-xs text-gray-400 mt-1">
                剩余 {{ remaining(coupon) }} / {{ coupon.totalCount }} 张
              </p>
            </div>

            <div class="mt-3">
              <button
                v-if="canClaim(coupon)"
                class="w-full bg-primary hover:bg-primary-600 text-white text-sm font-medium py-1.5 rounded transition-colors"
                :disabled="coupon._claiming"
                @click="handleClaim(coupon)"
              >
                {{ coupon._claiming ? '领取中...' : '立即领取' }}
              </button>
              <button
                v-else-if="isExpired(coupon)"
                class="w-full bg-gray-100 text-gray-400 text-sm py-1.5 rounded cursor-not-allowed"
                disabled
              >
                已过期
              </button>
              <button
                v-else
                class="w-full bg-gray-100 text-gray-400 text-sm py-1.5 rounded cursor-not-allowed"
                disabled
              >
                已领完
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Toast 提示 -->
    <Transition name="toast">
      <div v-if="toast.visible"
        :class="['fixed top-20 left-1/2 -translate-x-1/2 z-50 px-6 py-3 rounded-lg shadow-lg text-sm font-medium flex items-center gap-2',
          toast.type === 'success' ? 'bg-green-500 text-white' : 'bg-red-500 text-white']">
        {{ toast.message }}
      </div>
    </Transition>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { get, post } from '~/utils/request'
import { useUserStore } from '~/stores/user'

const route = useRoute()
// 场景区分：scene=new 为新人专享（只展示新人券），否则为领券中心（只展示普通券）
const isNewScene = computed(() => route.query.scene === 'new')
const pageTitle = computed(() => (isNewScene.value ? '新人专享' : '领券中心'))
const pageSubtitle = computed(() =>
  isNewScene.value ? '新用户专属福利，仅限首次下单前领取' : '优惠好礼，领取后下单更省')

const toast = reactive({ visible: false, message: '', type: 'success' as 'success' | 'error' })
let toastTimer: ReturnType<typeof setTimeout> | null = null
function showToast(message: string, type: 'success' | 'error' = 'success') {
  if (toastTimer) clearTimeout(toastTimer)
  toast.visible = true; toast.message = message; toast.type = type
  toastTimer = setTimeout(() => { toast.visible = false }, 2500)
}

interface Coupon {
  id: number
  name: string
  type: number
  discountValue: number
  minAmount: number
  totalCount: number
  claimedCount: number
  startTime: string
  endTime: string
  status: number
  _claiming?: boolean
}

const userStore = useUserStore()
const coupons = ref<Coupon[]>([])
const loading = ref(true)

const fetchCoupons = async () => {
  loading.value = true
  try {
    // scene=new 请求新人券(newUser=1)，否则请求普通券(newUser=0)
    const data = await get<any[]>('/coupon/list', { newUser: isNewScene.value ? 1 : 0 })
    coupons.value = (data || []).map(c => ({ ...c, _claiming: false }))
  } catch (e) {
    console.error('获取优惠券失败:', e)
  } finally {
    loading.value = false
  }
}

const typeText = (t: number) => ({ 1: '满减券', 2: '折扣券', 3: '无门槛券' } as Record<number, string>)[t] || '优惠券'
const typeClass = (t: number) =>
  ({ 1: 'bg-orange-50 text-orange-600', 2: 'bg-purple-50 text-purple-600', 3: 'bg-green-50 text-green-600' } as Record<number, string>)[t] || 'bg-gray-50 text-gray-600'

const minAmountText = (c: Coupon) => {
  if (c.type === 3 || c.minAmount <= 0) return '无门槛'
  return `满¥${c.minAmount}可用`
}

const remaining = (c: Coupon) => Math.max(0, c.totalCount - c.claimedCount)

const isExpired = (c: Coupon) => c.endTime && new Date(c.endTime) < new Date()
const canClaim = (c: Coupon) =>
  c.status === 1 && remaining(c) > 0 && !isExpired(c)

const formatDate = (dt: string) => {
  if (!dt) return ''
  return dt.substring(0, 10)
}

const handleClaim = async (coupon: Coupon) => {
  if (!userStore.isLoggedIn) {
    navigateTo('/login')
    return
  }
  if (coupon._claiming) return
  coupon._claiming = true
  try {
    await post(`/coupon/claim/${coupon.id}`)
    showToast('领取成功，快去使用吧！')
    fetchCoupons()
  } catch (e: any) {
    showToast(e.message || '领取失败', 'error')
  } finally {
    coupon._claiming = false
  }
}

onMounted(fetchCoupons)
</script>
