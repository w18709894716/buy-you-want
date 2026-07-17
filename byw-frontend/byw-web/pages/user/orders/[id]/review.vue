<template>
  <div class="max-w-3xl mx-auto px-4 py-6">
    <!-- 顶部导航 -->
    <div class="flex items-center gap-2 mb-6">
      <button class="text-gray-500 hover:text-gray-700" @click="navigateTo('/user/orders')">
        ← 返回订单
      </button>
      <h2 class="text-xl font-bold text-gray-800">发表评价</h2>
    </div>

    <!-- 加载状态 -->
    <div v-if="loading" class="bg-white rounded-xl p-16 text-center">
      <div class="animate-pulse text-gray-400">加载中...</div>
    </div>

    <!-- 订单不存在或已评价 -->
    <div v-else-if="!order" class="bg-white rounded-xl p-16 text-center">
      <div class="text-5xl mb-3">📦</div>
      <p class="text-gray-400">订单不存在</p>
    </div>

    <div v-else-if="alreadyReviewed" class="bg-white rounded-xl p-16 text-center">
      <div class="text-5xl mb-3">✅</div>
      <p class="text-gray-600 font-medium">该订单已评价</p>
      <p class="text-gray-400 text-sm mt-2">感谢您的评价</p>
      <button
        class="mt-6 px-6 py-2 bg-primary text-white rounded-lg hover:bg-primary-600 transition-colors"
        @click="navigateTo('/user/orders')"
      >
        返回订单列表
      </button>
    </div>

    <!-- 评价表单 -->
    <div v-else class="space-y-4">
      <!-- 订单信息概览 -->
      <div class="bg-white rounded-xl p-5">
        <div class="flex items-center justify-between text-sm">
          <span class="text-gray-500">订单号：{{ order.orderNo }}</span>
          <span class="text-gray-500">{{ order.createdAt }}</span>
        </div>
      </div>

      <!-- 商品评分提示 -->
      <div class="bg-amber-50 border border-amber-100 rounded-xl p-4">
        <p class="text-sm text-amber-700">
          <span class="text-base mr-1">💡</span>
          请对每件商品进行评价，您的真实评价能帮助其他买家做出更好的选择
        </p>
      </div>

      <!-- 逐商品评价卡片 -->
      <div v-for="(item, idx) in reviewItems" :key="item.skuId" class="bg-white rounded-xl overflow-hidden shadow-sm">
        <!-- 商品信息 -->
        <div class="p-4 border-b border-gray-100 flex items-center gap-3 bg-gray-50">
          <img :src="item.productImage || 'https://via.placeholder.com/60x60?text=商品'" class="w-14 h-14 object-cover rounded-lg flex-shrink-0" />
          <div class="flex-1 min-w-0">
            <p class="text-sm text-gray-800 font-medium truncate">{{ item.productName }}</p>
            <p class="text-xs text-gray-400 mt-0.5">{{ item.skuName }}</p>
          </div>
          <div class="text-right flex-shrink-0">
            <div class="text-sm text-gray-700">¥{{ item.price?.toFixed(2) }}</div>
            <div class="text-xs text-gray-400">x{{ item.quantity }}</div>
          </div>
        </div>

        <!-- 评分区 -->
        <div class="p-4 space-y-4">
          <div class="flex items-center gap-3">
            <span class="text-sm text-gray-600 flex-shrink-0">商品评分</span>
            <div class="flex items-center gap-1" @mouseleave="hoverRating[idx] = 0">
              <button
                v-for="star in 5"
                :key="star"
                class="text-2xl transition-transform hover:scale-110 focus:outline-none"
                :class="star <= (hoverRating[idx] || item.rating) ? 'text-yellow-400' : 'text-gray-200'"
                @mouseenter="hoverRating[idx] = star"
                @click="item.rating = star"
              >★</button>
              <span class="ml-2 text-sm" :class="ratingColorClass(item.rating)">
                {{ ratingText(item.rating) }}
              </span>
            </div>
          </div>

          <!-- 评价内容 -->
          <div>
            <textarea
              v-model="item.content"
              :placeholder="`请分享您对「${item.productName}」的使用感受...`"
              rows="4"
              maxlength="500"
              class="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm resize-none focus:outline-none focus:border-primary focus:ring-1 focus:ring-primary/20 transition-colors"
            />
            <div class="text-right text-xs text-gray-400 mt-1">
              {{ item.content?.length || 0 }}/500
            </div>
          </div>

          <!-- 图片上传（UI占位） -->
          <div>
            <div class="flex items-center gap-2 flex-wrap">
              <div
                v-for="(img, imgIdx) in item.images"
                :key="imgIdx"
                class="relative w-20 h-20 rounded-lg overflow-hidden group"
              >
                <img :src="img" class="w-full h-full object-cover" />
                <button
                  class="absolute inset-0 bg-black/40 opacity-0 group-hover:opacity-100 flex items-center justify-center text-white text-xs transition-opacity"
                  @click="item.images.splice(imgIdx, 1)"
                >删除</button>
              </div>
              <label
                v-if="item.images.length < 9"
                class="w-20 h-20 border-2 border-dashed border-gray-200 rounded-lg flex flex-col items-center justify-center cursor-pointer hover:border-primary transition-colors"
              >
                <span class="text-gray-300 text-2xl">+</span>
                <span class="text-xs text-gray-400 mt-0.5">上传图片</span>
                <input
                  type="file"
                  accept="image/*"
                  class="hidden"
                  @change="handleImageUpload($event, idx)"
                />
              </label>
            </div>
            <p class="text-xs text-gray-400 mt-1">最多上传9张图片，支持 JPG/PNG</p>
          </div>

          <!-- 匿名评价 -->
          <label class="flex items-center gap-2 cursor-pointer select-none">
            <input
              type="checkbox"
              v-model="item.isAnonymous"
              class="w-4 h-4 accent-primary"
            />
            <span class="text-sm text-gray-500">匿名评价</span>
          </label>
        </div>
      </div>

      <!-- 提交按钮 -->
      <div class="sticky bottom-4 bg-white rounded-xl p-4 shadow-lg border">
        <div class="flex items-center justify-between">
          <div class="text-sm text-gray-500">
            共 <span class="text-gray-800 font-medium">{{ reviewItems.length }}</span> 件商品待评价
          </div>
          <button
            :disabled="submitting || !allRated"
            class="px-8 py-2.5 bg-primary text-white rounded-lg font-medium hover:bg-primary-600 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
            @click="submitReviews"
          >
            {{ submitting ? '提交中...' : '发表评价' }}
          </button>
        </div>
        <p v-if="!allRated" class="text-xs text-orange-500 mt-2">请为所有商品评分后再提交</p>
      </div>
    </div>

    <!-- Toast 提示 -->
    <Teleport to="body">
      <TransitionGroup name="toast" tag="div" class="fixed top-4 left-1/2 -translate-x-1/2 z-[9999] flex flex-col items-center gap-2 pointer-events-none">
        <div
          v-for="t in toasts"
          :key="t.id"
          :class="[
            'px-5 py-2.5 rounded-lg shadow-lg text-sm font-medium pointer-events-auto',
            t.type === 'error' ? 'bg-red-500 text-white' : t.type === 'success' ? 'bg-green-500 text-white' : 'bg-gray-800 text-white'
          ]"
        >{{ t.message }}</div>
      </TransitionGroup>
    </Teleport>

    <!-- 成功弹窗 -->
    <Teleport to="body">
      <Transition name="modal">
        <div v-if="showSuccess" class="fixed inset-0 z-50 flex items-center justify-center p-4">
          <div class="fixed inset-0 bg-black/40" />
          <div class="relative bg-white rounded-2xl shadow-xl w-full max-w-sm p-8 text-center">
            <div class="text-5xl mb-4">🎉</div>
            <h3 class="text-lg font-bold text-gray-800 mb-2">评价成功！</h3>
            <p class="text-sm text-gray-500 mb-6">感谢您的评价，您的反馈对我们非常重要</p>
            <div class="flex gap-3 justify-center">
              <button
                class="px-5 py-2 border border-gray-300 text-gray-600 rounded-lg text-sm hover:bg-gray-50"
                @click="navigateTo('/user/orders')"
              >返回订单</button>
              <button
                class="px-5 py-2 bg-primary text-white rounded-lg text-sm hover:bg-primary-600"
                @click="navigateTo('/user/reviews')"
              >查看评价</button>
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
const alreadyReviewed = ref(false)
const submitting = ref(false)
const showSuccess = ref(false)

// ===== Toast 提示 =====
interface Toast { id: number; message: string; type: 'error' | 'success' | 'info' }
const toasts = ref<Toast[]>([])
let toastId = 0
function showToast(message: string, type: 'error' | 'success' | 'info' = 'error') {
  const id = ++toastId
  toasts.value.push({ id, message, type })
  setTimeout(() => {
    toasts.value = toasts.value.filter(t => t.id !== id)
  }, 3000)
}

interface ReviewItemForm {
  productId: number
  skuId: number
  productName: string
  skuName: string
  productImage: string
  price: number
  quantity: number
  rating: number
  content: string
  images: string[]
  isAnonymous: boolean
}

const reviewItems = ref<ReviewItemForm[]>([])
const hoverRating = reactive<Record<number, number>>({})

const allRated = computed(() =>
  reviewItems.value.length > 0 && reviewItems.value.every(item => item.rating >= 1)
)

function ratingText(rating: number): string {
  const texts = ['', '非常差', '较差', '一般', '满意', '非常满意']
  return texts[rating] || '请评分'
}

function ratingColorClass(rating: number): string {
  if (rating >= 4) return 'text-green-500'
  if (rating >= 3) return 'text-yellow-500'
  if (rating >= 1) return 'text-red-500'
  return 'text-gray-400'
}

/** 图片上传处理（本地预览） */
function handleImageUpload(event: Event, idx: number) {
  const input = event.target as HTMLInputElement
  if (!input.files?.length) return
  const file = input.files[0]
  if (file.size > 5 * 1024 * 1024) {
    showToast('图片大小不能超过 5MB')
    return
  }
  const reader = new FileReader()
  reader.onload = () => {
    reviewItems.value[idx].images.push(reader.result as string)
  }
  reader.readAsDataURL(file)
  input.value = ''
}

/** 获取订单信息 */
async function fetchOrder() {
  loading.value = true
  try {
    const data = await get(`/order/detail/${orderNo.value}`)
    order.value = data
    if (!data?.items?.length) {
      return
    }
    reviewItems.value = data.items.map((item: any) => ({
      productId: item.productId,
      skuId: item.skuId,
      productName: item.productName,
      skuName: item.skuName || '',
      productImage: item.productImage || '',
      price: item.price || 0,
      quantity: item.quantity || 1,
      rating: 5,        // 默认5星
      content: '',
      images: [],
      isAnonymous: false
    }))
  } catch (e) {
    console.error('获取订单详情失败:', e)
    order.value = null
  } finally {
    loading.value = false
  }
}

/** 检查是否已评价 */
async function checkReviewed() {
  try {
    const result = await get(`/review/exists/${orderNo.value}`)
    alreadyReviewed.value = !!result
  } catch (e) {
    // 接口不存在时不阻塞
  }
}

/** 提交评价 */
async function submitReviews() {
  if (!allRated.value || submitting.value) return
  submitting.value = true
  try {
    const reviews = reviewItems.value.map(item => ({
      orderNo: orderNo.value,
      productId: item.productId,
      skuId: item.skuId,
      rating: item.rating,
      content: item.content || '',
      images: item.images.length > 0 ? item.images : undefined,
      isAnonymous: item.isAnonymous ? 1 : 0
    }))
    await post('/review/create-batch', { reviews })
    showSuccess.value = true
  } catch (e: any) {
    showToast(e.message || '提交评价失败，请重试')
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  await Promise.all([fetchOrder(), checkReviewed()])
})
</script>

<style scoped>
.modal-enter-active, .modal-leave-active {
  transition: opacity 0.2s;
}
.modal-enter-from, .modal-leave-to {
  opacity: 0;
}
.toast-enter-active {
  transition: all 0.3s ease-out;
}
.toast-leave-active {
  transition: all 0.2s ease-in;
}
.toast-enter-from {
  opacity: 0;
  transform: translateY(-12px);
}
.toast-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}
</style>
