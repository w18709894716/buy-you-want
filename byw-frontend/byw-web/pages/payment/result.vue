<template>
  <div class="max-w-2xl mx-auto px-4 py-16">
    <!-- 确认中：轮询订单状态 -->
    <div v-if="state === 'checking'" class="bg-white rounded-lg p-12 text-center">
      <div class="w-16 h-16 mx-auto border-4 border-primary border-t-transparent rounded-full animate-spin" />
      <p class="text-lg font-medium text-gray-800 mt-6">支付确认中...</p>
      <p class="text-sm text-gray-400 mt-2">正在确认支付结果，请稍候</p>
    </div>

    <!-- 支付成功：订单状态已流转 -->
    <div v-else-if="state === 'success'" class="bg-white rounded-lg p-12 text-center">
      <div class="w-16 h-16 mx-auto bg-green-500 rounded-full flex items-center justify-center">
        <svg class="w-9 h-9 text-white" fill="none" stroke="currentColor" stroke-width="3" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" d="M5 13l4 4L19 7" />
        </svg>
      </div>
      <p class="text-xl font-bold text-gray-800 mt-6">支付成功</p>
      <p v-if="order?.payAmount != null" class="text-3xl font-bold text-primary mt-3">¥{{ order.payAmount.toFixed(2) }}</p>
      <p class="text-sm text-gray-400 mt-2 font-mono">订单编号：{{ orderNo }}</p>
      <div class="flex items-center justify-center gap-3 mt-8">
        <button class="h-11 px-8 bg-primary text-white rounded-lg font-medium hover:bg-primary-600 transition-colors" @click="navigateTo('/user/orders')">
          查看订单
        </button>
        <button class="h-11 px-8 border border-gray-300 text-gray-600 rounded-lg font-medium hover:border-gray-400 transition-colors" @click="navigateTo('/')">
          返回首页
        </button>
      </div>
    </div>

    <!-- 超时未确认：MQ 消费延迟，引导去订单列表 -->
    <div v-else class="bg-white rounded-lg p-12 text-center">
      <div class="w-16 h-16 mx-auto bg-orange-100 rounded-full flex items-center justify-center">
        <svg class="w-9 h-9 text-orange-500" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" d="M12 8v4m0 4h.01M12 21a9 9 0 100-18 9 9 0 000 18z" />
        </svg>
      </div>
      <p class="text-xl font-bold text-gray-800 mt-6">支付处理中</p>
      <p class="text-sm text-gray-400 mt-2">支付已提交，订单状态更新可能稍有延迟<br />请稍后在订单列表查看最新状态</p>
      <div class="flex items-center justify-center gap-3 mt-8">
        <button class="h-11 px-8 bg-primary text-white rounded-lg font-medium hover:bg-primary-600 transition-colors" @click="navigateTo('/user/orders')">
          查看订单
        </button>
        <button class="h-11 px-8 border border-gray-300 text-gray-600 rounded-lg font-medium hover:border-gray-400 transition-colors" @click="navigateTo('/')">
          返回首页
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
// 支付结果轮询页：支付回调后订单状态经 MQ 异步流转（0待付款 → 1待发货），
// 此页轮询订单状态直至流转完成，抹平"支付成功但订单仍显示待付款"的窗口期。
import { get } from '~/utils/request'

definePageMeta({ middleware: ['auth'] })

const route = useRoute()
const orderNo = route.query.orderNo as string

const state = ref<'checking' | 'success' | 'pending'>('checking')
const order = ref<any>(null)

const POLL_INTERVAL = 1500
const MAX_ATTEMPTS = 10 // 最多轮询约 15 秒
let attempts = 0
let pollTimer: ReturnType<typeof setInterval> | null = null

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

async function pollOrderStatus() {
  attempts++
  try {
    const data = await get<any>(`/order/detail/${orderNo}`)
    order.value = data
    // 状态离开待付款（0）即视为支付结果已传导到订单
    if (data && data.status !== 0) {
      state.value = 'success'
      stopPolling()
      return
    }
  } catch { /* 单次查询失败不中断轮询 */ }
  if (attempts >= MAX_ATTEMPTS) {
    state.value = 'pending'
    stopPolling()
  }
}

onMounted(() => {
  if (!orderNo) {
    navigateTo('/user/orders')
    return
  }
  pollOrderStatus()
  pollTimer = setInterval(pollOrderStatus, POLL_INTERVAL)
})

onUnmounted(() => {
  stopPolling()
})
</script>
