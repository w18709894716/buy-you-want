<template>
  <div class="min-h-screen bg-gray-50">
    <!-- 顶部标题栏 -->
    <div class="bg-gradient-to-r from-amber-500 to-orange-500 py-6">
      <div class="max-w-3xl mx-auto px-4">
        <h1 class="text-2xl font-bold text-white flex items-center gap-2">
          <span>📦</span> 物流查询
        </h1>
        <p class="text-white/80 text-sm mt-1">输入订单号，实时追踪包裹动态</p>
      </div>
    </div>

    <!-- 查询区 -->
    <div class="max-w-3xl mx-auto px-4 py-6">
      <div class="bg-white rounded-lg p-4 shadow-sm">
        <div class="flex gap-3">
          <input
            v-model="orderNo"
            type="text"
            class="flex-1 border border-gray-200 rounded px-4 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-primary-300 focus:border-primary"
            placeholder="请输入订单编号"
            @keyup.enter="handleQuery"
          />
          <button
            class="bg-primary hover:bg-primary-600 text-white px-6 py-2.5 rounded font-medium text-sm transition-colors whitespace-nowrap"
            :disabled="querying || !orderNo.trim()"
            @click="handleQuery"
          >
            {{ querying ? '查询中...' : '查询' }}
          </button>
        </div>
      </div>

      <!-- 查询结果 -->
      <div v-if="result" class="mt-4 bg-white rounded-lg shadow-sm overflow-hidden">
        <!-- 快递公司信息 -->
        <div class="p-4 border-b bg-gray-50">
          <div class="flex items-center gap-4">
            <div class="w-12 h-12 rounded-full bg-primary-100 flex items-center justify-center text-2xl">
              🚚
            </div>
            <div class="flex-1 min-w-0">
              <p class="font-medium text-gray-800">{{ result.companyName || '快递运输中' }}</p>
              <p class="text-sm text-gray-500 mt-0.5">
                运单号：<span class="font-mono">{{ result.trackingNo || '-' }}</span>
              </p>
              <p class="text-xs mt-1">
                <span
                  class="px-2 py-0.5 rounded text-xs"
                  :class="statusClass(result.status)"
                >
                  {{ statusText(result.status) }}
                </span>
              </p>
            </div>
          </div>
        </div>

        <!-- 物流轨迹时间线 -->
        <div class="p-4">
          <h3 class="text-sm font-medium text-gray-700 mb-4">物流轨迹</h3>
          <div v-if="traces.length" class="relative pl-6">
            <!-- 竖线 -->
            <div class="absolute left-2 top-1.5 bottom-1.5 w-px bg-gray-200" />
            <!-- 轨迹项 -->
            <div
              v-for="(trace, idx) in traces"
              :key="idx"
              class="relative pb-5 last:pb-0"
            >
              <!-- 圆点 -->
              <span
                class="absolute -left-5 top-1 w-3 h-3 rounded-full border-2"
                :class="idx === 0 ? 'bg-primary border-primary' : 'bg-white border-gray-300'"
              />
              <p
                class="text-sm"
                :class="idx === 0 ? 'text-gray-800 font-medium' : 'text-gray-600'"
              >
                {{ trace.description }}
              </p>
              <p class="text-xs text-gray-400 mt-0.5">
                {{ formatTime(trace.traceTime) }}
                <span v-if="trace.location" class="ml-2">{{ trace.location }}</span>
              </p>
            </div>
          </div>
          <div v-else class="text-center py-8 text-gray-400 text-sm">
            暂无物流轨迹
          </div>
        </div>
      </div>

      <!-- 错误/未找到 -->
      <div v-else-if="notFound" class="mt-4 bg-white rounded-lg p-12 text-center text-gray-400 shadow-sm">
        <p class="text-5xl mb-4">📭</p>
        <p>未找到该订单的物流信息</p>
        <p class="text-sm mt-2">请确认订单号是否正确，或包裹尚未发出</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { get } from '~/utils/request'

interface Trace {
  description: string
  location?: string
  traceTime: string
}

interface LogisticsResult {
  id: number
  orderNo: string
  companyCode?: string
  companyName?: string
  trackingNo?: string
  status: number
  traces?: Trace[]
}

const route = useRoute()
const orderNo = ref('')
const querying = ref(false)
const result = ref<LogisticsResult | null>(null)
const notFound = ref(false)

const traces = ref<Trace[]>([])

const statusText = (s: number) =>
  ({ 0: '已揽收', 1: '运输中', 2: '派送中', 3: '已签收', 4: '异常' } as Record<number, string>)[s] || '未知'

const statusClass = (s: number) =>
  ({
    0: 'bg-blue-50 text-blue-600',
    1: 'bg-amber-50 text-amber-600',
    2: 'bg-purple-50 text-purple-600',
    3: 'bg-green-50 text-green-600',
    4: 'bg-red-50 text-red-600',
  } as Record<number, string>)[s] || 'bg-gray-50 text-gray-600'

const formatTime = (t: string) => {
  if (!t) return ''
  return t.replace('T', ' ').substring(0, 16)
}

const handleQuery = async () => {
  const no = orderNo.value.trim()
  if (!no) return
  querying.value = true
  result.value = null
  notFound.value = false
  traces.value = []
  try {
    const data = await get<any>(`/logistics/track/${encodeURIComponent(no)}`)
    if (data) {
      result.value = data
      traces.value = (data.traces || []).slice().reverse()
    } else {
      notFound.value = true
    }
  } catch (e: any) {
    notFound.value = true
  } finally {
    querying.value = false
  }
}

onMounted(() => {
  const q = route.query.orderNo as string
  if (q) {
    orderNo.value = q
    handleQuery()
  }
})
</script>
