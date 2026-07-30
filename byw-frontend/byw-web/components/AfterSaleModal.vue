<template>
  <Teleport to="body">
    <Transition name="modal">
      <div v-if="modelValue" class="fixed inset-0 z-[60] flex items-center justify-center p-4">
        <div class="fixed inset-0 bg-black/40" @click="close" />
        <div class="relative bg-white rounded-lg shadow-xl w-full max-w-md p-6 max-h-[90vh] overflow-y-auto">
          <h3 class="text-base font-medium text-gray-800 mb-1">申请售后</h3>
          <p class="text-xs text-gray-400 mb-4">订单号：{{ order?.orderNo }}</p>

          <!-- 售后商品 -->
          <div class="flex items-center gap-3 bg-gray-50 rounded-lg p-3 mb-4">
            <img
              :src="item?.productImage || 'https://via.placeholder.com/56x56?text=商品'"
              :alt="item?.productName"
              class="w-14 h-14 object-cover rounded flex-shrink-0"
            />
            <div class="flex-1 min-w-0">
              <p class="text-sm text-gray-800 truncate">{{ item?.productName }}</p>
              <p class="text-xs text-gray-400 mt-0.5 truncate">{{ item?.skuName }}</p>
            </div>
            <div class="text-right flex-shrink-0">
              <div class="text-sm font-bold text-primary">¥{{ (item?.price || 0).toFixed(2) }}</div>
              <div class="text-xs text-gray-400">x{{ item?.quantity }}</div>
            </div>
          </div>

          <!-- 售后类型 -->
          <div class="mb-4">
            <p class="text-sm text-gray-700 mb-2">售后类型</p>
            <div class="grid grid-cols-3 gap-2">
              <button
                v-for="opt in afterSaleTypeOptions"
                :key="opt.value"
                :class="[
                  'px-3 py-2 text-sm border rounded transition-colors',
                  form.typeGroup === opt.value ? 'border-primary text-primary bg-primary-50' : 'border-gray-200 text-gray-600 hover:border-primary hover:text-primary'
                ]"
                @click="form.typeGroup = opt.value"
              >{{ opt.label }}</button>
            </div>
          </div>

          <!-- 退货/退款细分：仅退款 / 退货退款 -->
          <div v-if="form.typeGroup === 'refund'" class="mb-4">
            <p class="text-sm text-gray-700 mb-2">退款方式</p>
            <div class="grid grid-cols-2 gap-2">
              <button
                v-for="sub in refundSubOptions"
                :key="sub.value"
                :class="[
                  'px-3 py-2 text-sm border rounded transition-colors',
                  form.refundSubType === sub.value ? 'border-primary text-primary bg-primary-50' : 'border-gray-200 text-gray-600 hover:border-primary hover:text-primary'
                ]"
                @click="form.refundSubType = sub.value"
              >
                <span class="block font-medium">{{ sub.label }}</span>
                <span class="block text-xs text-gray-400 mt-0.5">{{ sub.desc }}</span>
              </button>
            </div>
          </div>

          <!-- 退款金额（仅退款/退货退款/价保时填写），上限为该商品明细小计 -->
          <div v-if="isRefundType" class="mb-4">
            <p class="text-sm text-gray-700 mb-2">退款金额</p>
            <input
              v-model="form.refundAmount"
              type="number"
              min="0.01"
              step="0.01"
              class="w-full border border-gray-200 rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-300 focus:border-primary"
              placeholder="请输入退款金额"
            />
            <p class="text-xs text-gray-400 mt-1">最多可退 ¥{{ maxRefund.toFixed(2) }}</p>
          </div>

          <!-- 申请原因 -->
          <div class="mb-4">
            <p class="text-sm text-gray-700 mb-2">申请原因</p>
            <select
              v-model="form.reason"
              class="w-full border border-gray-200 rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-300 focus:border-primary bg-white"
            >
              <option v-for="r in afterSaleReasons" :key="r" :value="r">{{ r }}</option>
            </select>
          </div>

          <!-- 问题描述 -->
          <div class="mb-5">
            <p class="text-sm text-gray-700 mb-2">问题描述（选填）</p>
            <textarea
              v-model="form.description"
              rows="3"
              maxlength="500"
              class="w-full border border-gray-200 rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-300 focus:border-primary resize-none"
              placeholder="请补充问题细节，便于商家快速处理"
            />
          </div>

          <div class="flex justify-end gap-3">
            <button
              class="px-4 h-9 text-sm text-gray-600 border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors"
              @click="close"
            >取消</button>
            <button
              class="px-4 h-9 text-sm text-white bg-primary rounded-lg hover:bg-primary-600 transition-colors disabled:opacity-60"
              :disabled="form.submitting"
              @click="submit"
            >{{ form.submitting ? '提交中...' : '提交申请' }}</button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { post } from '~/utils/request'

const props = defineProps<{
  modelValue: boolean
  /** 所属订单（需含 orderNo） */
  order: any
  /** 售后商品明细（需含 id/productName/skuName/productImage/price/quantity/subtotal） */
  item: any
}>()
const emit = defineEmits(['update:modelValue', 'toast', 'submitted'])

// 商品未发货时货未寄出，仅支持仅退款与价保（与后端 apply 校验一致）
const itemShipped = computed(() => props.item?.shipStatus === 1)

const allTypeOptions = [
  { label: '退货/退款', value: 'refund' },
  { label: '换货', value: 'exchange' },
  { label: '维修', value: 'repair' },
  { label: '补寄', value: 'reship' },
  { label: '价保', value: 'priceProtect' },
]
const afterSaleTypeOptions = computed(() =>
  itemShipped.value ? allTypeOptions : allTypeOptions.filter(o => o.value === 'refund' || o.value === 'priceProtect')
)
const refundSubOptions = computed(() => {
  const all = [
    { label: '仅退款', value: 1, desc: '未收到货或无需退货' },
    { label: '退货退款', value: 2, desc: '已收到货，需退还商品' },
  ]
  return itemShipped.value ? all : all.filter(s => s.value === 1)
})
const afterSaleReasons = ['质量问题', '商品与描述不符', '七天无理由退换', '少件/漏发', '收到商品破损', '商品降价', '其他']

const form = reactive({
  typeGroup: 'refund',
  refundSubType: 1,
  refundAmount: '',
  reason: '质量问题',
  description: '',
  submitting: false
})

// 最终售后类型：1仅退款 2退货退款 3换货 4维修 5补寄 6价保
const afterSaleType = computed(() => {
  const map: Record<string, number> = { exchange: 3, repair: 4, reship: 5, priceProtect: 6 }
  return form.typeGroup === 'refund' ? form.refundSubType : map[form.typeGroup]
})
// 退款类售后（仅退款/退货退款/价保）需填写退款金额
const isRefundType = computed(() => [1, 2, 6].includes(afterSaleType.value))

// 退款上限 = 该商品明细小计
const maxRefund = computed(() => {
  const it = props.item
  if (!it) return 0
  return it.subtotal != null ? Number(it.subtotal) : Number(it.price || 0) * (it.quantity || 1)
})

watch(() => props.modelValue, (v) => {
  if (v) {
    form.typeGroup = 'refund'
    form.refundSubType = 1
    form.refundAmount = maxRefund.value ? String(maxRefund.value) : ''
    form.reason = '质量问题'
    form.description = ''
  }
})

async function submit() {
  if (!props.order || !props.item) return
  let refundAmount: number | null = null
  if (isRefundType.value) {
    refundAmount = parseFloat(form.refundAmount)
    if (!refundAmount || refundAmount <= 0) {
      emit('toast', { message: '请填写正确的退款金额', type: 'error' })
      return
    }
    if (refundAmount > maxRefund.value) {
      emit('toast', { message: '退款金额不能超过该商品实付小计', type: 'error' })
      return
    }
  }
  form.submitting = true
  try {
    await post('/order/aftersale/apply', {
      orderNo: props.order.orderNo,
      orderItemId: props.item.id,
      type: afterSaleType.value,
      reason: form.reason,
      description: form.description,
      refundAmount
    })
    emit('update:modelValue', false)
    emit('toast', { message: '售后申请已提交，请等待商家处理', type: 'success' })
    emit('submitted')
  } catch (e: any) {
    emit('toast', { message: e?.message || '售后申请提交失败', type: 'error' })
  } finally {
    form.submitting = false
  }
}

function close() {
  emit('update:modelValue', false)
}
</script>

<style scoped>
.modal-enter-active, .modal-leave-active { transition: all 0.3s ease; }
.modal-enter-from, .modal-leave-to { opacity: 0; }
</style>
