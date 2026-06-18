<template>
  <div class="max-w-7xl mx-auto px-4 py-6">
    <h2 class="text-xl font-bold text-gray-800 mb-6">确认订单</h2>

    <div class="flex gap-6">
      <div class="flex-1 space-y-4">
        <!-- 收货地址 -->
        <div class="bg-white rounded-lg p-6">
          <h3 class="font-medium text-gray-800 mb-4 flex items-center gap-2">
            <span class="w-6 h-6 bg-primary text-white rounded-full text-xs flex items-center justify-center">1</span>
            收货地址
          </h3>

          <!-- 地址列表 -->
          <div class="space-y-3">
            <label
              v-for="addr in addresses"
              :key="addr.id"
              :class="[
                'block border rounded-lg p-4 cursor-pointer transition-all',
                selectedAddressId === addr.id ? 'border-primary bg-primary-50' : 'border-gray-200 hover:border-gray-300'
              ]"
            >
              <input type="radio" :value="addr.id" v-model="selectedAddressId" class="hidden" />
              <div class="flex items-center gap-4">
                <div class="flex-1">
                  <div class="flex items-center gap-3">
                    <span class="font-medium">{{ addr.name }}</span>
                    <span class="text-gray-500 text-sm">{{ addr.phone }}</span>
                    <span v-if="addr.isDefault" class="text-xs bg-primary text-white px-2 py-0.5 rounded">默认</span>
                  </div>
                  <p class="text-sm text-gray-600 mt-1">{{ addr.address }}</p>
                </div>
              </div>
            </label>
          </div>

          <button class="mt-3 text-sm text-primary hover:text-primary-600 flex items-center gap-1">
            <span>+</span> 新增收货地址
          </button>
        </div>

        <!-- 商品清单 -->
        <div class="bg-white rounded-lg p-6">
          <h3 class="font-medium text-gray-800 mb-4 flex items-center gap-2">
            <span class="w-6 h-6 bg-primary text-white rounded-full text-xs flex items-center justify-center">2</span>
            商品清单
          </h3>
          <div class="divide-y">
            <div v-for="item in orderItems" :key="item.cartId" class="py-3 flex items-center gap-4">
              <img
                :src="item.image || 'https://via.placeholder.com/60x60?text=商品'"
                :alt="item.productName"
                class="w-16 h-16 object-cover rounded"
              />
              <div class="flex-1">
                <p class="text-sm text-gray-800">{{ item.productName }}</p>
                <p v-if="item.skuSpecs" class="text-xs text-gray-400 mt-0.5">{{ item.skuSpecs }}</p>
              </div>
              <div class="text-sm text-gray-800">¥{{ item.price.toFixed(2) }}</div>
              <div class="text-sm text-gray-500">x{{ item.quantity }}</div>
              <div class="text-sm font-bold text-primary">¥{{ (item.price * item.quantity).toFixed(2) }}</div>
            </div>
          </div>
        </div>

        <!-- 优惠券 -->
        <div class="bg-white rounded-lg p-6">
          <h3 class="font-medium text-gray-800 mb-4 flex items-center gap-2">
            <span class="w-6 h-6 bg-primary text-white rounded-full text-xs flex items-center justify-center">3</span>
            优惠券
          </h3>
          <div class="flex gap-3">
            <input
              v-model="couponCode"
              type="text"
              placeholder="输入优惠券码"
              class="flex-1 h-10 px-4 border border-gray-300 rounded-lg outline-none focus:border-primary text-sm"
            />
            <button
              class="h-10 px-6 bg-primary text-white rounded-lg text-sm hover:bg-primary-600 transition-colors"
              @click="applyCoupon"
            >
              使用
            </button>
          </div>
          <div v-if="appliedCoupon" class="mt-2 text-sm text-green-600 flex items-center gap-1">
            <span>✓</span> 已使用优惠券：{{ appliedCoupon.name }}，优惠 ¥{{ appliedCoupon.discount.toFixed(2) }}
          </div>
        </div>

        <!-- 订单备注 -->
        <div class="bg-white rounded-lg p-6">
          <h3 class="font-medium text-gray-800 mb-4 flex items-center gap-2">
            <span class="w-6 h-6 bg-primary text-white rounded-full text-xs flex items-center justify-center">4</span>
            订单备注
          </h3>
          <textarea
            v-model="remark"
            placeholder="选填：请输入订单备注（如配送时间、特殊要求等）"
            rows="3"
            class="w-full px-4 py-2 border border-gray-300 rounded-lg outline-none focus:border-primary text-sm resize-none"
          ></textarea>
        </div>
      </div>

      <!-- 右侧结算信息 -->
      <div class="w-80 flex-shrink-0">
        <div class="bg-white rounded-lg p-6 sticky top-40">
          <h3 class="font-medium text-gray-800 mb-4">订单汇总</h3>
          <div class="space-y-3 text-sm">
            <div class="flex justify-between text-gray-600">
              <span>商品总额</span>
              <span>¥{{ subtotal.toFixed(2) }}</span>
            </div>
            <div class="flex justify-between text-gray-600">
              <span>运费</span>
              <span class="text-green-500">免运费</span>
            </div>
            <div v-if="appliedCoupon" class="flex justify-between text-gray-600">
              <span>优惠券</span>
              <span class="text-green-500">-¥{{ appliedCoupon.discount.toFixed(2) }}</span>
            </div>
          </div>
          <div class="border-t mt-4 pt-4">
            <div class="flex justify-between items-baseline">
              <span class="text-gray-800 font-medium">应付总额</span>
              <span class="text-2xl font-bold text-primary">¥{{ totalAmount.toFixed(2) }}</span>
            </div>
          </div>

          <div class="mt-4 text-xs text-gray-400 space-y-1">
            <p>收货人：{{ selectedAddress?.name || '请选择地址' }}</p>
            <p>联系电话：{{ selectedAddress?.phone || '-' }}</p>
          </div>

          <button
            :disabled="!selectedAddressId || submitting"
            class="w-full h-12 mt-4 bg-primary text-white rounded-lg font-medium hover:bg-primary-600 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
            @click="submitOrder"
          >
            {{ submitting ? '提交中...' : '提交订单' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useCartStore } from '~/stores/cart'

definePageMeta({ middleware: ['auth'] })

const cartStore = useCartStore()

const orderItems = ref(cartStore.checkedItems)
const selectedAddressId = ref<number>(1)
const couponCode = ref('')
const appliedCoupon = ref<{ name: string; discount: number } | null>(null)
const remark = ref('')
const submitting = ref(false)

// 占位地址数据
const addresses = ref([
  { id: 1, name: '张三', phone: '138****8888', address: '北京市朝阳区建国路88号SOHO现代城A座1201室', isDefault: true },
  { id: 2, name: '李四', phone: '139****9999', address: '上海市浦东新区陆家嘴金融中心B座2503室', isDefault: false },
])

const selectedAddress = computed(() => addresses.value.find(a => a.id === selectedAddressId.value))

const subtotal = computed(() => orderItems.value.reduce((sum, item) => sum + item.price * item.quantity, 0))

const totalAmount = computed(() => {
  let total = subtotal.value
  if (appliedCoupon.value) {
    total -= appliedCoupon.value.discount
  }
  return Math.max(0, total)
})

function applyCoupon() {
  if (!couponCode.value.trim()) return
  // 模拟优惠券验证
  if (couponCode.value.trim().toUpperCase() === 'BYW100') {
    appliedCoupon.value = { name: '满500减100', discount: 100 }
  } else {
    alert('无效的优惠券码')
  }
}

async function submitOrder() {
  if (!selectedAddressId.value) {
    alert('请选择收货地址')
    return
  }

  submitting.value = true
  try {
    // TODO: 调用后端接口创建订单
    await new Promise(resolve => setTimeout(resolve, 1000))
    alert('订单提交成功！')
    navigateTo('/user/orders')
  } catch (error: any) {
    alert(error?.message || '订单提交失败')
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  if (orderItems.value.length === 0) {
    navigateTo('/cart')
  }
})
</script>
