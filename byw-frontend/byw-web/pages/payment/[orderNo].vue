<template>
  <div class="max-w-2xl mx-auto px-4 py-8">
    <!-- Toast -->
    <Transition name="toast">
      <div
        v-if="toast.visible"
        :class="[
          'fixed top-20 left-1/2 -translate-x-1/2 z-50 px-6 py-3 rounded-lg shadow-lg text-sm font-medium flex items-center gap-2',
          toast.type === 'success' ? 'bg-green-500 text-white' : 'bg-red-500 text-white'
        ]"
      >
        <span>{{ toast.type === 'success' ? '✓' : '✕' }}</span>
        <span>{{ toast.message }}</span>
      </div>
    </Transition>

    <div v-if="order" class="space-y-6">
      <!-- 订单信息 -->
      <div class="bg-white rounded-lg p-6 text-center">
        <p class="text-sm text-gray-500 mb-1">订单编号</p>
        <p class="text-sm text-gray-800 font-mono">{{ order.orderNo }}</p>
        <p class="text-3xl font-bold text-primary mt-4">¥{{ order.payAmount?.toFixed(2) }}</p>
      </div>

      <!-- 倒计时 -->
      <div class="bg-white rounded-lg p-6 text-center">
        <p class="text-sm text-gray-500 mb-2">请在以下时间内完成支付</p>
        <div v-if="remainingSeconds > 0" class="flex items-center justify-center gap-1">
          <span class="bg-gray-800 text-white text-2xl font-mono font-bold px-3 py-2 rounded">{{ Math.floor(remainingSeconds / 60) }}</span>
          <span class="text-2xl font-bold text-gray-800">:</span>
          <span class="bg-gray-800 text-white text-2xl font-mono font-bold px-3 py-2 rounded">{{ (remainingSeconds % 60).toString().padStart(2, '0') }}</span>
          <span class="text-sm text-gray-500 ml-2">分钟</span>
        </div>
        <div v-else class="text-red-500 font-medium">
          <p class="text-lg">订单已超时</p>
          <button class="mt-2 text-sm text-primary hover:text-primary-600" @click="navigateTo('/user/orders')">查看订单</button>
        </div>
      </div>

      <!-- 支付方式 -->
      <div v-if="remainingSeconds > 0" class="bg-white rounded-lg p-6">
        <h3 class="font-medium text-gray-800 mb-4">选择支付方式</h3>
        <div class="space-y-2">
          <label
            v-for="method in paymentMethods"
            :key="method.id"
            :class="[
              'flex items-center gap-4 border rounded-lg p-4 cursor-pointer transition-all',
              selectedMethod === method.id ? 'border-primary bg-primary-50' : 'border-gray-200 hover:border-gray-300'
            ]"
            @click="selectedMethod = method.id"
          >
            <div :class="['w-10 h-10 rounded-full flex items-center justify-center text-white text-sm font-bold', method.color]">
              {{ method.icon }}
            </div>
            <div class="flex-1">
              <p class="text-sm font-medium text-gray-800">{{ method.name }}</p>
              <p class="text-xs text-gray-400">{{ method.desc }}</p>
            </div>
            <div :class="['w-5 h-5 rounded-full border-2 flex items-center justify-center', selectedMethod === method.id ? 'border-primary' : 'border-gray-300']">
              <div v-if="selectedMethod === method.id" class="w-3 h-3 rounded-full bg-primary" />
            </div>
          </label>
        </div>

        <button
          :disabled="!selectedMethod || paying"
          class="w-full h-12 mt-6 bg-primary text-white rounded-lg font-medium hover:bg-primary-600 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
          @click="confirmPay"
        >
          {{ paying ? '支付中...' : '确认支付 ¥' + order.payAmount?.toFixed(2) }}
        </button>
      </div>

      <!-- 订单商品 -->
      <div class="bg-white rounded-lg p-6">
        <h3 class="font-medium text-gray-800 mb-4">订单商品</h3>
        <div class="divide-y">
          <div v-for="item in order.items" :key="item.skuId" class="py-3 flex items-center gap-3">
            <img :src="item.productImage || 'https://via.placeholder.com/40x40?text=商品'" class="w-10 h-10 rounded object-cover" />
            <div class="flex-1 min-w-0">
              <p class="text-sm text-gray-800 truncate">{{ item.productName }}</p>
              <p class="text-xs text-gray-400">{{ item.skuName }} x{{ item.quantity }}</p>
            </div>
            <p class="text-sm text-gray-800">¥{{ item.price?.toFixed(2) }}</p>
          </div>
        </div>
      </div>
    </div>

    <div v-else class="bg-white rounded-lg p-16 text-center">
      <p class="text-gray-400">订单不存在</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { get, post } from '~/utils/request'

definePageMeta({ middleware: ['auth'] })

const route = useRoute()
const orderNo = route.params.orderNo as string

const order = ref<any>(null)
const paying = ref(false)
const selectedMethod = ref<string>('wechat')
const remainingSeconds = ref(0)
let countdownTimer: ReturnType<typeof setInterval> | null = null

// Toast
const toast = reactive({ visible: false, message: '', type: 'success' as 'success' | 'error' })
let toastTimer: ReturnType<typeof setTimeout> | null = null
function showToast(message: string, type: 'success' | 'error' = 'success') {
  if (toastTimer) clearTimeout(toastTimer)
  toast.visible = true
  toast.message = message
  toast.type = type
  toastTimer = setTimeout(() => { toast.visible = false }, 2500)
}

const paymentMethods = [
  { id: 'wechat', name: '微信支付', desc: '推荐使用', icon: '微', color: 'bg-green-500' },
  { id: 'alipay', name: '支付宝', desc: '支付宝快捷支付', icon: '支', color: 'bg-blue-500' },
  { id: 'unionpay', name: '云闪付', desc: '银联快捷支付', icon: '银', color: 'bg-red-500' },
  { id: 'bank', name: '银行卡支付', desc: '网银/借记卡', icon: '卡', color: 'bg-gray-600' },
]

async function fetchOrder() {
  try {
    const data = await get<any>(`/order/detail/${orderNo}`)
    order.value = data
    // 计算倒计时：创建时间 + 30 分钟
    if (data.createdAt) {
      const createdAt = new Date(data.createdAt).getTime()
      const deadline = createdAt + 30 * 60 * 1000
      updateCountdown(deadline)
      countdownTimer = setInterval(() => updateCountdown(deadline), 1000)
    }
  } catch { /* ignore */ }
}

function updateCountdown(deadline: number) {
  const now = Date.now()
  remainingSeconds.value = Math.max(0, Math.floor((deadline - now) / 1000))
  if (remainingSeconds.value <= 0 && countdownTimer) {
    clearInterval(countdownTimer)
    countdownTimer = null
  }
}

async function confirmPay() {
  if (!order.value || remainingSeconds.value <= 0) return
  paying.value = true
  try {
    // TODO: 接入真实支付
    await new Promise(resolve => setTimeout(resolve, 1500))
    // 模拟支付成功：更新订单状态为已支付
    await post(`/order/pay/${orderNo}`)
    showToast('支付成功！', 'success')
    setTimeout(() => navigateTo('/user/orders'), 1500)
  } catch (e: any) {
    showToast(e?.message || '支付失败，请重试', 'error')
  } finally {
    paying.value = false
  }
}

onMounted(() => {
  fetchOrder()
})

onUnmounted(() => {
  if (countdownTimer) clearInterval(countdownTimer)
})
</script>

<style scoped>
.toast-enter-active, .toast-leave-active { transition: all 0.3s ease; }
.toast-enter-from, .toast-leave-to { opacity: 0; transform: translate(-50%, -10px); }
.toast-enter-to, .toast-leave-from { opacity: 1; transform: translate(-50%, 0); }
</style>
