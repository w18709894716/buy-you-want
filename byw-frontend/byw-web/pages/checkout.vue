<template>
  <div class="max-w-7xl mx-auto px-4 py-6">
    <!-- Toast 通知 -->
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

    <h2 class="text-xl font-bold text-gray-800 mb-6">确认订单</h2>

    <div class="flex flex-col lg:flex-row gap-6">
      <div class="flex-1 min-w-0 space-y-4">
        <!-- 收货地址 -->
        <div class="bg-white rounded-lg p-6">
          <h3 class="font-medium text-gray-800 mb-4 flex items-center gap-2">
            <span class="w-6 h-6 bg-primary text-white rounded-full text-xs flex items-center justify-center">1</span>
            收货地址
          </h3>

          <div v-if="selectedAddress" class="flex items-center gap-4">
            <div class="flex-1 border rounded-lg p-4 border-primary bg-primary-50">
              <div class="flex items-center gap-3">
                <span class="font-medium">{{ selectedAddress.receiverName }}</span>
                <span class="text-gray-500 text-sm">{{ selectedAddress.receiverPhone }}</span>
                <span v-if="selectedAddress.isDefault" class="text-xs bg-primary text-white px-2 py-0.5 rounded">默认</span>
              </div>
              <p class="text-sm text-gray-600 mt-1">{{ formatAddress(selectedAddress) }}</p>
            </div>
            <button class="text-gray-400 hover:text-primary transition-colors" @click="showAddressPicker = true">
              <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7"/></svg>
            </button>
          </div>
          <div v-else class="text-center py-6 text-gray-400">
            <p>暂无收货地址</p>
            <button class="mt-2 text-sm text-primary hover:text-primary-600" @click="navigateTo('/user/address')">去添加</button>
          </div>
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
              <div class="flex-1 min-w-0">
                <p class="text-sm text-gray-800 truncate">{{ item.productName }}</p>
                <div class="flex items-center gap-2 mt-1">
                  <span class="text-xs text-gray-400">{{ formatSpecs(item.specData) || '默认规格' }}</span>
                  <button class="text-xs text-primary hover:text-primary-600" @click="openSpecSwitcher(item)">更换</button>
                </div>
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
          <div v-if="appliedCoupon" class="flex items-center gap-4">
            <div class="flex-1 border rounded-lg p-4 border-primary bg-primary-50">
              <div class="flex items-center gap-3">
                <span class="text-lg font-bold text-primary">-¥{{ appliedCoupon.discount.toFixed(2) }}</span>
                <span class="text-sm text-gray-800">{{ appliedCoupon.name }}</span>
              </div>
              <p class="text-xs text-gray-500 mt-1">{{ appliedCoupon.condition }}</p>
            </div>
            <button class="text-gray-400 hover:text-primary transition-colors" @click="showCouponPicker = true">
              <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7"/></svg>
            </button>
          </div>
          <div v-else class="flex items-center gap-4">
            <div class="flex-1 border rounded-lg p-4 border-gray-200 text-gray-500 text-sm">
              未使用优惠券
            </div>
            <button class="text-gray-400 hover:text-primary transition-colors" @click="showCouponPicker = true">
              <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7"/></svg>
            </button>
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
      <div class="w-full lg:w-80 lg:flex-shrink-0">
        <div class="bg-white rounded-lg p-6 lg:sticky lg:top-40">
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
            <p>收货人：{{ selectedAddress?.receiverName || '请选择地址' }}</p>
            <p>联系电话：{{ selectedAddress?.receiverPhone || '-' }}</p>
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

    <!-- 地址选择弹窗 -->
    <Teleport to="body">
      <Transition name="modal">
        <div v-if="showAddressPicker" class="fixed inset-0 z-50 flex items-center justify-center p-4">
          <div class="fixed inset-0 bg-black/40" @click="showAddressPicker = false" />
          <div class="relative bg-white rounded-lg shadow-xl w-full max-w-md p-6 max-h-[80vh] flex flex-col">
            <h3 class="text-base font-medium text-gray-800 mb-4">选择收货地址</h3>
            <div class="flex-1 overflow-y-auto space-y-2">
              <label
                v-for="addr in addresses"
                :key="addr.id"
                :class="[
                  'block border rounded-lg p-3 cursor-pointer transition-all',
                  selectedAddressId === addr.id ? 'border-primary bg-primary-50' : 'border-gray-200 hover:border-gray-300'
                ]"
                @click="selectedAddressId = addr.id; showAddressPicker = false"
              >
                <div class="flex items-center gap-3">
                  <span class="font-medium text-sm">{{ addr.receiverName }}</span>
                  <span class="text-gray-500 text-xs">{{ addr.receiverPhone }}</span>
                  <span v-if="addr.isDefault" class="text-xs bg-primary text-white px-1.5 py-0.5 rounded">默认</span>
                </div>
                <p class="text-xs text-gray-500 mt-1">{{ formatAddress(addr) }}</p>
              </label>
            </div>
            <div class="border-t pt-3 mt-3">
              <button class="w-full h-10 border border-dashed border-gray-300 rounded-lg text-sm text-gray-500 hover:border-primary hover:text-primary transition-colors" @click="showAddressPicker = false; navigateTo('/user/address')">
                + 新增收货地址
              </button>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>

    <!-- 规格切换弹窗 -->
    <Teleport to="body">
      <Transition name="modal">
        <div v-if="specSwitcherItem" class="fixed inset-0 z-50 flex items-center justify-center p-4">
          <div class="fixed inset-0 bg-black/40" @click="specSwitcherItem = null" />
          <div class="relative bg-white rounded-lg shadow-xl w-full max-w-md p-6 max-h-[80vh] flex flex-col">
            <h3 class="text-base font-medium text-gray-800 mb-2">{{ specSwitcherItem.productName }}</h3>
            <p class="text-lg font-bold text-primary mb-4">¥{{ specSwitcherNewPrice.toFixed(2) }}</p>
            <div v-if="specSwitcherSkuList.length > 0" class="flex-1 overflow-y-auto">
              <div v-for="group in specSwitcherGroups" :key="group.name" class="mb-3">
                <p class="text-sm text-gray-600 mb-2">{{ group.name }}</p>
                <div class="flex flex-wrap gap-2">
                  <button
                    v-for="opt in group.options"
                    :key="opt"
                    :class="[
                      'px-3 py-1.5 border rounded text-sm transition-all',
                      specSwitcherSelected[group.name] === opt
                        ? 'border-primary bg-primary-50 text-primary'
                        : 'border-gray-200 text-gray-600 hover:border-gray-300'
                    ]"
                    @click="specSwitcherSelected[group.name] = opt"
                  >{{ opt }}</button>
                </div>
              </div>
            </div>
            <div class="border-t pt-3 mt-3 flex justify-end gap-3">
              <button class="px-4 h-9 text-sm text-gray-600 border border-gray-300 rounded-lg hover:bg-gray-50" @click="specSwitcherItem = null">取消</button>
              <button class="px-4 h-9 text-sm text-white bg-primary rounded-lg hover:bg-primary-600" @click="confirmSpecSwitch">确定</button>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>

    <!-- 优惠券选择弹窗 -->
    <Teleport to="body">
      <Transition name="modal">
        <div v-if="showCouponPicker" class="fixed inset-0 z-50 flex items-center justify-center p-4">
          <div class="fixed inset-0 bg-black/40" @click="showCouponPicker = false" />
          <div class="relative bg-white rounded-lg shadow-xl w-full max-w-md p-6 max-h-[80vh] flex flex-col">
            <h3 class="text-base font-medium text-gray-800 mb-4">选择优惠券</h3>
            <div class="flex-1 overflow-y-auto space-y-2">
              <!-- 可用优惠券 -->
              <template v-if="availableCoupons.length > 0">
                <p class="text-xs text-gray-400 font-medium">可使用</p>
                <label
                  v-for="coupon in availableCoupons"
                  :key="coupon.couponId"
                  :class="[
                    'flex items-center gap-3 border rounded-lg p-3 cursor-pointer transition-all',
                    selectedCouponId === coupon.couponId ? 'border-primary bg-primary-50' : 'border-gray-200 hover:border-gray-300'
                  ]"
                  @click="selectCoupon(coupon); showCouponPicker = false"
                >
                  <div class="w-14 h-14 bg-primary-50 rounded flex flex-col items-center justify-center text-primary flex-shrink-0">
                    <span class="text-xs">{{ coupon.type === 2 ? '折' : '减' }}</span>
                    <span class="text-base font-bold">{{ coupon.type === 2 ? (coupon.discountValue * 10).toFixed(1) : '¥' + coupon.discountValue.toFixed(0) }}</span>
                  </div>
                  <div class="flex-1 min-w-0">
                    <p class="text-sm font-medium text-gray-800">{{ coupon.name }}</p>
                    <p class="text-xs text-gray-400 mt-0.5">{{ coupon.minAmount > 0 ? '满¥' + coupon.minAmount.toFixed(0) + '可用' : '无门槛' }}</p>
                  </div>
                  <span class="text-xs text-green-500 flex-shrink-0">省¥{{ calcCouponDiscount(coupon).toFixed(2) }}</span>
                </label>
              </template>
              <!-- 不使用 -->
              <label
                :class="[
                  'flex items-center gap-3 border rounded-lg p-3 cursor-pointer transition-all',
                  selectedCouponId === null ? 'border-primary bg-primary-50' : 'border-gray-200 hover:border-gray-300'
                ]"
                @click="selectedCouponId = null; appliedCoupon = null; showCouponPicker = false"
              >
                <div class="w-14 h-14 bg-gray-50 rounded flex items-center justify-center text-gray-400 flex-shrink-0">
                  <span class="text-xs">不使用</span>
                </div>
                <div class="flex-1">
                  <p class="text-sm font-medium text-gray-600">不使用优惠券</p>
                </div>
              </label>
              <!-- 不可用优惠券 -->
              <template v-if="unavailableCoupons.length > 0">
                <p class="text-xs text-gray-400 font-medium mt-3">不可使用</p>
                <div
                  v-for="coupon in unavailableCoupons"
                  :key="coupon.couponId"
                  class="flex items-center gap-3 border border-gray-100 rounded-lg p-3 opacity-50"
                >
                  <div class="w-14 h-14 bg-gray-50 rounded flex flex-col items-center justify-center text-gray-300 flex-shrink-0">
                    <span class="text-xs">{{ coupon.type === 2 ? '折' : '减' }}</span>
                    <span class="text-base font-bold">{{ coupon.type === 2 ? (coupon.discountValue * 10).toFixed(1) : '¥' + coupon.discountValue.toFixed(0) }}</span>
                  </div>
                  <div class="flex-1 min-w-0">
                    <p class="text-sm text-gray-500">{{ coupon.name }}</p>
                    <p class="text-xs text-red-400 mt-0.5">{{ getUnavailableReason(coupon) }}</p>
                  </div>
                </div>
              </template>
              <!-- 无任何优惠券 -->
              <div v-if="userCoupons.length === 0" class="text-center py-8 text-gray-400 text-sm">
                <p>暂无优惠券</p>
              </div>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { useCartStore } from '~/stores/cart'
import { get, post } from '~/utils/request'

definePageMeta({ middleware: ['auth'] })

const cartStore = useCartStore()

// ===== 基础数据 =====
const orderItems = ref<any[]>(cartStore.checkedItems.map(item => ({ ...item })))
const remark = ref('')
const submitting = ref(false)

// Toast 通知
const toast = reactive({ visible: false, message: '', type: 'success' as 'success' | 'error' })
let toastTimer: ReturnType<typeof setTimeout> | null = null
function showToast(message: string, type: 'success' | 'error' = 'success') {
  if (toastTimer) clearTimeout(toastTimer)
  toast.visible = true
  toast.message = message
  toast.type = type
  toastTimer = setTimeout(() => { toast.visible = false }, 2500)
}

// ===== 收货地址 =====
const addresses = ref<any[]>([])
const selectedAddressId = ref<number | null>(null)
const showAddressPicker = ref(false)

const selectedAddress = computed(() => addresses.value.find(a => a.id === selectedAddressId.value))

function formatAddress(addr: any): string {
  return `${addr.province || ''}${addr.city || ''}${addr.district || ''}${addr.detailAddress || ''}`
}

async function fetchAddresses() {
  try {
    const data = await get<any[]>('/user/address/list')
    addresses.value = data
    const defaultAddr = data.find(a => a.isDefault === 1)
    if (defaultAddr) selectedAddressId.value = defaultAddr.id
    else if (data.length > 0) selectedAddressId.value = data[0].id
  } catch { /* ignore */ }
}

// ===== 计算属性（提前定义，优惠券逻辑依赖） =====
const subtotal = computed(() => orderItems.value.reduce((sum, item) => sum + item.price * item.quantity, 0))

// ===== 优惠券 =====
const userCoupons = ref<any[]>([])
const selectedCouponId = ref<number | null>(null)
const showCouponPicker = ref(false)
const appliedCoupon = ref<{ name: string; discount: number; condition: string } | null>(null)

// 判断优惠券是否可用
function isCouponAvailable(coupon: any): boolean {
  // 检查是否过期
  if (coupon.endTime && new Date(coupon.endTime) < new Date()) return false
  // 检查是否满足门槛
  if (coupon.minAmount && coupon.minAmount > 0 && subtotal.value < coupon.minAmount) return false
  return true
}

// 获取不可用原因
function getUnavailableReason(coupon: any): string {
  if (coupon.endTime && new Date(coupon.endTime) < new Date()) return '已过期'
  if (coupon.minAmount && coupon.minAmount > 0 && subtotal.value < coupon.minAmount) {
    return `还差¥${(coupon.minAmount - subtotal.value).toFixed(2)}可用`
  }
  return '不可用'
}

// 计算优惠券可省金额
function calcCouponDiscount(coupon: any): number {
  const orderAmount = subtotal.value
  if (coupon.type === 1) { // 满减券
    if (orderAmount >= (coupon.minAmount || 0)) return Math.min(coupon.discountValue, orderAmount)
  } else if (coupon.type === 2) { // 折扣券
    if (orderAmount >= (coupon.minAmount || 0)) {
      return Math.round((orderAmount - orderAmount * coupon.discountValue) * 100) / 100
    }
  } else if (coupon.type === 3) { // 无门槛券
    return Math.min(coupon.discountValue, orderAmount)
  }
  return 0
}

// 可用优惠券（按优惠力度排序）
const availableCoupons = computed(() =>
  userCoupons.value.filter(c => isCouponAvailable(c)).sort((a, b) => calcCouponDiscount(b) - calcCouponDiscount(a))
)

// 不可用优惠券
const unavailableCoupons = computed(() =>
  userCoupons.value.filter(c => !isCouponAvailable(c))
)

async function fetchCoupons() {
  try {
    const data = await get<any[]>('/coupon/my-coupons?status=0')
    userCoupons.value = data
    // 自动选最优惠的券
    autoSelectBestCoupon()
  } catch { /* ignore */ }
}

function autoSelectBestCoupon() {
  const available = userCoupons.value
    .filter(c => isCouponAvailable(c))
    .sort((a, b) => calcCouponDiscount(b) - calcCouponDiscount(a))
  if (available.length > 0) {
    selectCoupon(available[0])
  }
}

function selectCoupon(coupon: any) {
  selectedCouponId.value = coupon.couponId
  const discount = calcCouponDiscount(coupon)
  const condition = coupon.minAmount > 0 ? `满¥${coupon.minAmount.toFixed(0)}可用` : '无门槛'
  appliedCoupon.value = discount > 0
    ? { name: coupon.name, discount: Math.round(discount * 100) / 100, condition }
    : null
}

// 订单金额变化时重新评估优惠券可用性
watch(subtotal, () => {
  if (selectedCouponId.value) {
    const current = userCoupons.value.find(c => c.couponId === selectedCouponId.value)
    if (current && !isCouponAvailable(current)) {
      // 当前选的券不可用了，自动换最优券
      autoSelectBestCoupon()
    } else if (current) {
      // 重新计算优惠金额
      selectCoupon(current)
    }
  }
})

// ===== 规格切换 =====
const specSwitcherItem = ref<any>(null)
const specSwitcherSkuList = ref<any[]>([])
const specSwitcherGroups = ref<any[]>([])
const specSwitcherSelected = ref<Record<string, string>>({})
const specSwitcherNewPrice = ref(0)

async function openSpecSwitcher(item: any) {
  specSwitcherItem.value = item
  try {
    const data = await get<any>(`/product/${item.productId}`)
    const skus = data.skus || []
    specSwitcherSkuList.value = skus
    // 解析当前规格
    const currentSpecs = parseSpecsObj(item.specData)
    specSwitcherSelected.value = { ...currentSpecs }
    specSwitcherNewPrice.value = item.price
    // 构建规格组
    const groupMap: Record<string, Set<string>> = {}
    skus.forEach((sku: any) => {
      const sd = typeof sku.specData === 'string' ? JSON.parse(sku.specData) : sku.specData
      Object.entries(sd).forEach(([k, v]) => {
        if (!groupMap[k]) groupMap[k] = new Set()
        groupMap[k].add(v as string)
      })
    })
    specSwitcherGroups.value = Object.entries(groupMap).map(([name, opts]) => ({
      name, options: Array.from(opts)
    }))
  } catch { /* ignore */ }
}

watch(specSwitcherSelected, () => {
  // 实时匹配 SKU 价格
  const matched = specSwitcherSkuList.value.find((sku: any) => {
    const sd = typeof sku.specData === 'string' ? JSON.parse(sku.specData) : sku.specData
    return Object.entries(specSwitcherSelected.value).every(([k, v]) => sd[k] === v)
  })
  if (matched) specSwitcherNewPrice.value = matched.price
}, { deep: true })

async function confirmSpecSwitch() {
  const item = specSwitcherItem.value
  if (!item) return
  // 找到匹配的 SKU
  const matched = specSwitcherSkuList.value.find((sku: any) => {
    const sd = typeof sku.specData === 'string' ? JSON.parse(sku.specData) : sku.specData
    return Object.entries(specSwitcherSelected.value).every(([k, v]) => sd[k] === v)
  })
  if (!matched) { showToast('未找到匹配的规格组合', 'error'); return }
  // 更新购物车
  try {
    await cartStore.changeSku(item.cartId, matched.id)
    // 更新本地 orderItems
    const idx = orderItems.value.findIndex(i => i.cartId === item.cartId)
    if (idx >= 0) {
      orderItems.value[idx].skuId = matched.id
      orderItems.value[idx].price = matched.price
      orderItems.value[idx].specData = typeof matched.specData === 'string' ? matched.specData : JSON.stringify(matched.specData)
    }
    specSwitcherItem.value = null
    showToast('规格已更换')
  } catch (e: any) {
    showToast(e?.message || '更换失败', 'error')
  }
}

// ===== 计算属性 =====
const totalAmount = computed(() => {
  let total = subtotal.value
  if (appliedCoupon.value) total -= appliedCoupon.value.discount
  return Math.max(0, total)
})

// ===== 提交订单 =====
async function submitOrder() {
  if (!selectedAddressId.value) { showToast('请选择收货地址', 'error'); return }
  submitting.value = true
  try {
    const orderData = {
      addressId: selectedAddressId.value,
      couponId: selectedCouponId.value,
      remark: remark.value,
      items: orderItems.value.map(item => ({
        productId: item.productId,
        skuId: item.skuId,
        productName: item.productName,
        skuName: formatSpecs(item.specData) || '默认规格',
        productImage: item.image,
        price: item.price,
        quantity: item.quantity,
      })),
    }
    const orderNo = await post<string>('/order/create', orderData)
    for (const item of orderItems.value) {
      await cartStore.removeItem(item.cartId)
    }
    showToast('订单提交成功！', 'success')
    setTimeout(() => navigateTo('/payment/' + orderNo), 800)
  } catch (error: any) {
    showToast(error?.message || '订单提交失败', 'error')
  } finally {
    submitting.value = false
  }
}

// ===== 工具函数 =====
function parseSpecs(specData: string): string {
  if (!specData) return ''
  try {
    const obj = typeof specData === 'string' ? JSON.parse(specData) : specData
    return Object.entries(obj).map(([k, v]) => `${k}:${v}`).join(' / ')
  } catch { return '' }
}

function parseSpecsObj(specData: string): Record<string, string> {
  if (!specData) return {}
  try {
    const obj = typeof specData === 'string' ? JSON.parse(specData) : specData
    return { ...obj }
  } catch { return {} }
}

function formatSpecs(specData: string): string {
  if (!specData) return ''
  try {
    const obj = typeof specData === 'string' ? JSON.parse(specData) : specData
    return Object.values(obj).join(' / ')
  } catch { return '' }
}

onMounted(() => {
  if (orderItems.value.length === 0) { navigateTo('/cart'); return }
  fetchAddresses()
  fetchCoupons()
})
</script>

<style scoped>
.toast-enter-active, .toast-leave-active { transition: all 0.3s ease; }
.toast-enter-from, .toast-leave-to { opacity: 0; transform: translate(-50%, -10px); }
.toast-enter-to, .toast-leave-from { opacity: 1; transform: translate(-50%, 0); }
.modal-enter-active, .modal-leave-active { transition: all 0.3s ease; }
.modal-enter-from, .modal-leave-to { opacity: 0; }
.modal-enter-to, .modal-leave-from { opacity: 1; }
</style>
