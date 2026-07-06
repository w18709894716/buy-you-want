<template>
  <div class="max-w-4xl mx-auto px-4 py-6">
    <div class="flex items-center gap-2 mb-6">
      <button class="text-gray-500 hover:text-gray-700" @click="navigateTo('/user/orders')">
        ← 返回
      </button>
      <h2 class="text-xl font-bold text-gray-800">订单详情</h2>
    </div>

    <div v-if="loading" class="bg-white rounded-lg p-16 text-center">
      <p class="text-gray-400">加载中...</p>
    </div>

    <div v-else-if="order" class="space-y-4">
      <!-- 订单状态 -->
      <div class="bg-white rounded-lg p-6">
        <div class="flex items-center justify-between">
          <div>
            <div class="text-sm text-gray-500">订单号</div>
            <div class="text-lg font-medium text-gray-800 mt-1">{{ order.orderNo }}</div>
          </div>
          <div :class="statusClassMap[order.status]" class="text-lg font-medium">
            {{ statusTextMap[order.status] || '未知状态' }}
          </div>
        </div>
      </div>

      <!-- 收货信息 -->
      <div class="bg-white rounded-lg p-6">
        <h3 class="font-medium text-gray-800 mb-4">收货信息</h3>
        <div class="text-sm text-gray-600 space-y-1">
          <p>收货人：{{ order.receiverName || '-' }}</p>
          <p>联系电话：{{ order.receiverPhone || '-' }}</p>
          <p>收货地址：{{ order.receiverAddress || '-' }}</p>
        </div>
      </div>

      <!-- 商品清单 -->
      <div class="bg-white rounded-lg p-6">
        <h3 class="font-medium text-gray-800 mb-4">商品清单</h3>
        <div class="divide-y">
          <div v-for="item in order.items" :key="item.skuId" class="py-3 flex items-center gap-4">
            <img :src="item.productImage || 'https://via.placeholder.com/60x60?text=商品'" class="w-16 h-16 object-cover rounded" />
            <div class="flex-1">
              <p class="text-sm text-gray-800">{{ item.productName }}</p>
              <p class="text-xs text-gray-400 mt-0.5">{{ item.skuName }}</p>
            </div>
            <div class="text-sm text-gray-800">¥{{ item.price?.toFixed(2) }}</div>
            <div class="text-sm text-gray-500">x{{ item.quantity }}</div>
          </div>
        </div>
      </div>

      <!-- 订单信息 -->
      <div class="bg-white rounded-lg p-6">
        <h3 class="font-medium text-gray-800 mb-4">订单信息</h3>
        <div class="text-sm text-gray-600 space-y-2">
          <div class="flex justify-between">
            <span>商品总额</span>
            <span>¥{{ order.totalAmount?.toFixed(2) }}</span>
          </div>
          <div class="flex justify-between">
            <span>运费</span>
            <span class="text-green-500">免运费</span>
          </div>
          <div v-if="order.couponAmount" class="flex justify-between">
            <span>优惠券</span>
            <span class="text-green-500">-¥{{ order.couponAmount?.toFixed(2) }}</span>
          </div>
          <div class="border-t pt-2 mt-2 flex justify-between items-baseline">
            <span class="font-medium">实付金额</span>
            <span class="text-xl font-bold text-primary">¥{{ order.payAmount?.toFixed(2) }}</span>
          </div>
          <div class="flex justify-between mt-2">
            <span>下单时间</span>
            <span>{{ order.createdAt }}</span>
          </div>
          <div v-if="order.remark" class="flex justify-between">
            <span>订单备注</span>
            <span>{{ order.remark }}</span>
          </div>
        </div>
      </div>

      <!-- 操作按钮 -->
      <div class="bg-white rounded-lg p-6 flex justify-end gap-3">
        <button
          v-if="order.status === 0"
          class="px-6 py-2 bg-primary text-white rounded-lg hover:bg-primary-600 transition-colors"
          @click="handlePay"
        >
          立即付款
        </button>
        <button
          v-if="order.status === 2"
          class="px-6 py-2 bg-primary text-white rounded-lg hover:bg-primary-600 transition-colors"
          @click="handleConfirmReceive"
        >
          确认收货
        </button>
        <button
          v-if="order.status === 0"
          class="px-6 py-2 border border-gray-300 text-gray-600 rounded-lg hover:bg-gray-50 transition-colors"
          @click="handleCancel"
        >
          取消订单
        </button>
      </div>
    </div>

    <div v-else class="bg-white rounded-lg p-16 text-center">
      <p class="text-gray-400">订单不存在</p>
    </div>

    <!-- 确认弹窗 -->
    <Teleport to="body">
      <Transition name="modal">
        <div v-if="confirmDialog" class="fixed inset-0 z-50 flex items-center justify-center p-4">
          <div class="fixed inset-0 bg-black/40" @click="confirmDialog = null" />
          <div class="relative bg-white rounded-lg shadow-xl w-full max-w-sm p-6">
            <h3 class="text-base font-medium text-gray-800 mb-2">{{ confirmDialog.title }}</h3>
            <p class="text-sm text-gray-500 mb-5">{{ confirmDialog.message }}</p>
            <div class="flex justify-end gap-3">
              <button
                class="px-4 h-9 text-sm text-gray-600 border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors"
                @click="confirmDialog = null"
              >取消</button>
              <button
                class="px-4 h-9 text-sm text-white bg-primary rounded-lg hover:bg-primary-600 transition-colors"
                @click="confirmDialog.onConfirm()"
              >确定</button>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { get, post } from '~/utils/request'

definePageMeta({ middleware: ['auth'] })

const route = useRoute()
const orderNo = computed(() => route.params.id as string)

const loading = ref(true)
const order = ref<any>(null)

const statusTextMap: Record<number, string> = {
  0: '待付款',
  1: '待发货',
  2: '待收货',
  3: '已完成',
  4: '已取消',
  5: '退款中',
  6: '已退款'
}

const statusClassMap: Record<number, string> = {
  0: 'text-red-500',
  1: 'text-orange-500',
  2: 'text-blue-500',
  3: 'text-green-500',
  4: 'text-gray-500',
  5: 'text-yellow-500',
  6: 'text-gray-400'
}

const fetchOrderDetail = async () => {
  loading.value = true
  try {
    const data = await get(`/order/detail/${orderNo.value}`)
    order.value = data
  } catch (e) {
    console.error('获取订单详情失败:', e)
    order.value = null
  } finally {
    loading.value = false
  }
}

const confirmDialog = ref<{ title: string; message: string; onConfirm: () => void } | null>(null)

function handlePay() {
  alert(`订单 ${orderNo.value} 进入支付流程`)
}

function handleConfirmReceive() {
  confirmDialog.value = {
    title: '确认收货',
    message: '确认已收到该商品？',
    onConfirm: async () => {
      confirmDialog.value = null
      try {
        await post(`/order/confirm/${orderNo.value}`)
        fetchOrderDetail()
      } catch (e) {
        console.error('确认收货失败:', e)
      }
    }
  }
}

function handleCancel() {
  confirmDialog.value = {
    title: '取消订单',
    message: '确定要取消该订单吗？',
    onConfirm: async () => {
      confirmDialog.value = null
      try {
        await post(`/order/cancel/${orderNo.value}`, null, { params: { reason: '用户主动取消' } })
        fetchOrderDetail()
      } catch (e) {
        console.error('取消订单失败:', e)
      }
    }
  }
}

onMounted(() => {
  fetchOrderDetail()
})
</script>
