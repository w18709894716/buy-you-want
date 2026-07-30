<template>
  <Teleport to="body">
    <Transition name="modal">
      <div v-if="modelValue" class="fixed inset-0 z-[60] flex items-center justify-center p-4">
        <div class="fixed inset-0 bg-black/40" @click="close" />
        <div class="relative bg-white rounded-lg shadow-xl w-full max-w-md p-6 max-h-[90vh] overflow-y-auto">
          <h3 class="text-base font-medium text-gray-800 mb-1">退款明细</h3>
          <p class="text-xs text-gray-400 mb-4">订单号：{{ orderNo }}</p>

          <div v-if="loading" class="py-10 text-center text-sm text-gray-400">加载中...</div>

          <template v-else-if="detail">
            <!-- 售后商品（商品级售后展示快照，历史订单级售后无此信息） -->
            <div v-if="detail.productName" class="bg-gray-50 rounded-lg p-3 mb-3 text-sm">
              <p class="text-gray-800 truncate">{{ detail.productName }}</p>
              <p v-if="detail.skuName" class="text-xs text-gray-400 mt-0.5 truncate">{{ detail.skuName }}</p>
            </div>
            <!-- 退款概要 -->
            <div class="bg-gray-50 rounded-lg p-3 mb-4 text-sm space-y-1.5">
              <div class="flex justify-between">
                <span class="text-gray-500">售后类型</span>
                <span class="text-gray-800">{{ typeText(detail.type) }}</span>
              </div>
              <div class="flex justify-between">
                <span class="text-gray-500">当前状态</span>
                <span :class="afterSaleStatusClass(detail.status)" class="font-medium">{{ afterSaleStatusText(detail.status) }}</span>
              </div>
              <div class="flex justify-between">
                <span class="text-gray-500">退款金额</span>
                <span class="text-primary font-bold">¥{{ Number(detail.refundAmount || 0).toFixed(2) }}</span>
              </div>
              <div v-if="detail.refundNo" class="flex justify-between">
                <span class="text-gray-500">退款单号</span>
                <span class="text-gray-800 font-mono text-xs">{{ detail.refundNo }}</span>
              </div>
              <div v-if="detail.payChannel" class="flex justify-between">
                <span class="text-gray-500">退款去向</span>
                <span class="text-gray-800">原路退回 · {{ channelText(detail.payChannel) }}</span>
              </div>
              <div v-if="detail.refundNo" class="flex justify-between">
                <span class="text-gray-500">到账状态</span>
                <span :class="detail.refundStatus === 1 ? 'text-green-600' : 'text-yellow-600'">{{ refundStatusText(detail.refundStatus) }}</span>
              </div>
              <div v-if="detail.rejectReason" class="flex justify-between">
                <span class="text-gray-500">拒绝原因</span>
                <span class="text-red-500">{{ detail.rejectReason }}</span>
              </div>
            </div>

            <!-- 流程时间线 -->
            <div class="mb-4">
              <p class="text-sm text-gray-700 mb-3">退款进度</p>
              <div class="relative pl-6">
                <div
                  v-for="(node, idx) in detail.timeline"
                  :key="idx"
                  class="relative pb-4 last:pb-0"
                >
                  <!-- 竖线 -->
                  <div
                    v-if="idx < detail.timeline.length - 1"
                    class="absolute left-[-14px] top-4 w-px h-full"
                    :class="node.reached ? 'bg-primary' : 'bg-gray-200'"
                  />
                  <!-- 节点圆点 -->
                  <div
                    class="absolute left-[-19px] top-1 w-2.5 h-2.5 rounded-full border-2"
                    :class="node.reached ? 'bg-primary border-primary' : 'bg-white border-gray-300'"
                  />
                  <div class="flex items-center justify-between">
                    <span class="text-sm" :class="node.reached ? 'text-gray-800 font-medium' : 'text-gray-400'">{{ node.title }}</span>
                    <span v-if="node.time" class="text-xs text-gray-400">{{ formatTime(node.time) }}</span>
                  </div>
                </div>
              </div>
            </div>

            <!-- 待买家寄回：填写寄回单号 -->
            <div v-if="detail.status === 1" class="border-t pt-4">
              <p class="text-sm text-gray-700 mb-2 font-medium">填写寄回物流</p>
              <select
                v-model="shipForm.company"
                class="w-full border border-gray-200 rounded px-3 py-2 text-sm mb-2 focus:outline-none focus:ring-2 focus:ring-primary-300 focus:border-primary bg-white"
              >
                <option value="">请选择物流公司</option>
                <option v-for="c in companies" :key="c" :value="c">{{ c }}</option>
              </select>
              <input
                v-model="shipForm.trackingNo"
                class="w-full border border-gray-200 rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-300 focus:border-primary"
                placeholder="请输入寄回运单号"
              />
              <button
                class="w-full mt-3 px-4 h-9 text-sm text-white bg-primary rounded-lg hover:bg-primary-600 transition-colors disabled:opacity-60"
                :disabled="submitting"
                @click="submitReturnShipping"
              >{{ submitting ? '提交中...' : '提交寄回信息' }}</button>
            </div>
          </template>

          <div v-else class="py-10 text-center text-sm text-gray-400">暂无退款记录</div>

          <div class="flex justify-end mt-5">
            <button
              class="px-4 h-9 text-sm text-gray-600 border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors"
              @click="close"
            >关闭</button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { get, post } from '~/utils/request'

const props = defineProps<{
  modelValue: boolean
  orderNo: string
  /** 商品级售后：按订单明细ID定位售后单；不传则取订单最新一条（兼容历史订单级售后） */
  itemId?: number | null
}>()
const emit = defineEmits(['update:modelValue', 'toast', 'refreshed'])

const loading = ref(false)
const submitting = ref(false)
const detail = ref<any>(null)
const shipForm = reactive({ company: '', trackingNo: '' })
const companies = ['顺丰速运', '中通快递', '圆通速递', '韵达快递', '京东物流', '邮政EMS']

const typeText = (t: number) => (({ 1: '仅退款', 2: '退货退款' } as Record<number, string>)[t] || '退款')

const afterSaleStatusText = (s: number) =>
  (({ 0: '待审核', 1: '待买家寄回', 2: '已拒绝', 3: '已完成', 4: '已撤销', 5: '待商家收货', 6: '退款中' } as Record<number, string>)[s] || '未知')
const afterSaleStatusClass = (s: number) =>
  (({ 0: 'text-orange-500', 1: 'text-blue-500', 2: 'text-red-500', 3: 'text-green-500', 4: 'text-gray-400', 5: 'text-blue-500', 6: 'text-yellow-500' } as Record<number, string>)[s] || 'text-gray-500')

const refundStatusText = (s: number) =>
  (({ 0: '处理中', 1: '已到账', 2: '退款失败' } as Record<number, string>)[s] || '处理中')

const channelText = (c: string) =>
  (({ WECHAT: '微信支付', ALIPAY: '支付宝', BALANCE: '余额' } as Record<string, string>)[c] || c)

function formatTime(time: any) {
  if (!time) return ''
  if (Array.isArray(time)) {
    const [y, mo, d, h, mi] = time
    return `${y}-${String(mo).padStart(2, '0')}-${String(d).padStart(2, '0')} ${String(h || 0).padStart(2, '0')}:${String(mi || 0).padStart(2, '0')}`
  }
  return String(time).replace('T', ' ').substring(0, 16)
}

async function fetchDetail() {
  loading.value = true
  detail.value = null
  try {
    detail.value = await get(`/order/aftersale/refund-detail/${props.orderNo}`, props.itemId != null ? { itemId: props.itemId } : undefined)
  } catch (e: any) {
    emit('toast', { message: e?.message || '获取退款明细失败', type: 'error' })
  } finally {
    loading.value = false
  }
}

async function submitReturnShipping() {
  if (!shipForm.company) {
    emit('toast', { message: '请选择物流公司', type: 'error' })
    return
  }
  if (!shipForm.trackingNo || !shipForm.trackingNo.trim()) {
    emit('toast', { message: '请填写寄回运单号', type: 'error' })
    return
  }
  submitting.value = true
  try {
    await post(`/order/aftersale/return-shipping/${detail.value.afterSaleId}`, {
      company: shipForm.company,
      trackingNo: shipForm.trackingNo
    })
    emit('toast', { message: '寄回信息已提交', type: 'success' })
    emit('refreshed')
    await fetchDetail()
  } catch (e: any) {
    emit('toast', { message: e?.message || '提交失败', type: 'error' })
  } finally {
    submitting.value = false
  }
}

function close() {
  emit('update:modelValue', false)
}

watch(() => props.modelValue, (v) => {
  if (v && props.orderNo) {
    shipForm.company = ''
    shipForm.trackingNo = ''
    fetchDetail()
  }
})
</script>

<style scoped>
.modal-enter-active, .modal-leave-active { transition: all 0.3s ease; }
.modal-enter-from, .modal-leave-to { opacity: 0; }
</style>
