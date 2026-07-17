<template>
  <div class="max-w-7xl mx-auto px-4 py-6 relative">
    <!-- 加载中 -->
    <div v-if="loading" class="flex items-center justify-center py-20">
      <span class="text-gray-400">加载中...</span>
    </div>

    <template v-else>
    <!-- Toast 通知 -->
    <Transition name="toast">
      <div
        v-if="toast.visible"
        :class="[
          'fixed top-20 left-1/2 -translate-x-1/2 z-50 px-6 py-3 rounded-lg shadow-lg text-sm font-medium flex items-center gap-2 transition-all',
          toast.type === 'success' ? 'bg-green-500 text-white' : 'bg-red-500 text-white'
        ]"
      >
        <span>{{ toast.type === 'success' ? '✓' : '✕' }}</span>
        <span>{{ toast.message }}</span>
      </div>
    </Transition>

    <!-- 面包屑 -->
    <nav class="text-sm text-gray-500 mb-4">
      <NuxtLink to="/" class="hover:text-primary">首页</NuxtLink>
      <span class="mx-2">/</span>
      <NuxtLink to="/search" class="hover:text-primary">全部商品</NuxtLink>
      <span class="mx-2">/</span>
      <span class="text-gray-800">{{ product.title }}</span>
    </nav>

    <!-- 商品详情主体 -->
    <div class="bg-white rounded-lg p-4 sm:p-6">
      <div class="flex flex-col md:flex-row gap-6 md:gap-8">
        <!-- 左侧图片画廊 -->
        <div class="w-full md:w-96 md:flex-shrink-0">
          <!-- 主图 -->
          <div class="aspect-square bg-gray-100 rounded-lg overflow-hidden mb-3">
            <img
              :src="product.images[currentImageIndex] || product.images[0]"
              :alt="product.title"
              class="w-full h-full object-cover"
            />
          </div>
          <!-- 缩略图列表 -->
          <div class="flex gap-2">
            <button
              v-for="(img, index) in product.images"
              :key="index"
              :class="[
                'w-16 h-16 rounded border-2 overflow-hidden flex-shrink-0',
                currentImageIndex === index ? 'border-primary' : 'border-gray-200'
              ]"
              @click="currentImageIndex = index"
            >
              <img :src="img" :alt="`缩略图${index + 1}`" class="w-full h-full object-cover" />
            </button>
          </div>
        </div>

        <!-- 右侧商品信息 -->
        <div class="flex-1">
          <h1 class="text-xl font-bold text-gray-900 leading-tight">{{ product.title }}</h1>
          <p class="text-sm text-gray-500 mt-2">{{ product.subtitle }}</p>

          <!-- 价格区 -->
          <div class="bg-red-50 rounded-lg p-4 mt-4">
            <div class="flex items-baseline gap-3">
              <span class="text-sm text-gray-500">促销价</span>
              <span class="text-3xl font-bold text-primary">¥{{ product.price ? product.price.toFixed(2) : '0.00' }}</span>
              <span v-if="product.originalPrice" class="text-gray-400 line-through text-sm">¥{{ product.originalPrice.toFixed(2) }}</span>
            </div>
            <div class="mt-2 flex gap-2">
              <span class="text-xs bg-primary text-white px-2 py-0.5 rounded">满减</span>
              <span class="text-xs bg-primary text-white px-2 py-0.5 rounded">包邮</span>
              <span class="text-xs bg-primary text-white px-2 py-0.5 rounded">7天无理由</span>
            </div>
          </div>

          <!-- 销量信息 -->
          <div class="flex flex-wrap gap-x-6 gap-y-2 mt-4 text-sm text-gray-500">
            <span>累计销量：<span class="text-gray-800">{{ product.salesCount }}</span></span>
            <span>累计评价：<span class="text-gray-800">{{ product.reviewCount }}</span></span>
            <span>店铺：<span class="text-primary">{{ product.shopName }}</span></span>
          </div>

          <!-- SKU 选择器 -->
          <div class="mt-6">
            <SkuSelector v-if="specGroups.length > 0" :spec-groups="specGroups" :sku-list="skuListForSelector" @change="onSpecChange" />
          </div>

          <!-- 数量选择 -->
          <div class="mt-6 flex items-center gap-4">
            <span class="text-sm text-gray-600">购买数量</span>
            <div class="flex items-center border rounded">
              <button
                class="w-8 h-8 flex items-center justify-center hover:bg-gray-100 disabled:opacity-50"
                :disabled="quantity <= 1"
                @click="quantity--"
              >
                -
              </button>
              <input
                v-model.number="quantity"
                type="number"
                min="1"
                :max="product.stock"
                class="w-14 h-8 text-center border-x outline-none text-sm"
                @blur="clampQuantity"
              />
              <button
                class="w-8 h-8 flex items-center justify-center hover:bg-gray-100 disabled:opacity-50"
                :disabled="quantity >= product.stock"
                @click="quantity++"
              >
                +
              </button>
            </div>
            <span class="text-xs text-gray-400">库存 {{ product.stock }} 件</span>
          </div>

          <!-- 操作按钮 -->
          <div class="mt-8 flex gap-4">
            <button
              class="flex-1 h-12 bg-primary text-white rounded-lg font-medium hover:bg-primary-600 transition-colors flex items-center justify-center gap-2"
              @click="handleAddToCart"
            >
              <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" viewBox="0 0 20 20" fill="currentColor">
                <path d="M3 1a1 1 0 000 2h1.22l.305 1.222a.997.997 0 00.01.042l1.358 5.43-.893.892C3.74 11.846 4.632 14 6.414 14H15a1 1 0 000-2H6.414l1-1H14a1 1 0 00.894-.553l3-6A1 1 0 0017 3H6.28l-.31-1.243A1 1 0 005 1H3z" />
              </svg>
              加入购物车
            </button>
            <button
              class="flex-1 h-12 bg-orange-500 text-white rounded-lg font-medium hover:bg-orange-600 transition-colors"
              @click="handleBuyNow"
            >
              立即购买
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- 商品详情 Tab -->
    <div class="bg-white rounded-lg mt-6">
      <div class="border-b flex">
        <button
          v-for="tab in detailTabs"
          :key="tab.value"
          :class="[
            'px-4 sm:px-8 py-3 text-sm font-medium border-b-2 transition-colors',
            activeTab === tab.value ? 'border-primary text-primary' : 'border-transparent text-gray-600 hover:text-gray-800'
          ]"
          @click="activeTab = tab.value"
        >
          {{ tab.label }}
        </button>
      </div>

      <!-- 商品描述 -->
      <div v-if="activeTab === 'detail'" class="p-6">
        <div class="prose max-w-none text-gray-600 text-sm leading-relaxed">
          <h3 class="text-lg font-medium text-gray-800 mb-4">商品描述</h3>
          <p>{{ product.description }}</p>
          <div class="mt-6 grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div v-for="(value, key) in product.specs" :key="key" class="flex border-b py-2">
              <span class="w-32 text-gray-500 flex-shrink-0">{{ key }}</span>
              <span class="text-gray-800">{{ value }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 评价区域 -->
      <div v-if="activeTab === 'reviews'" class="p-6">
        <div class="flex items-center gap-6 mb-6">
          <div class="text-center">
            <div class="text-3xl font-bold text-primary">{{ product.rating }}</div>
            <div class="text-sm text-gray-500 mt-1">综合评分</div>
          </div>
          <div class="flex gap-1">
            <span v-for="i in 5" :key="i" class="text-xl" :class="i <= Math.round(product.rating) ? 'text-yellow-400' : 'text-gray-200'">★</span>
          </div>
        </div>

        <!-- 评论列表 -->
        <div class="space-y-6">
          <div v-for="review in reviews" :key="review.id" class="border-b pb-4">
            <div class="flex items-center gap-2 mb-2">
              <div class="w-8 h-8 bg-gray-200 rounded-full flex items-center justify-center text-sm text-gray-500">
                {{ review.username[0] }}
              </div>
              <span class="text-sm font-medium">{{ review.username }}</span>
              <span class="text-xs text-gray-400">{{ review.date }}</span>
            </div>
            <div class="flex gap-0.5 mb-2">
              <span v-for="i in 5" :key="i" class="text-sm" :class="i <= review.rating ? 'text-yellow-400' : 'text-gray-200'">★</span>
            </div>
            <p class="text-sm text-gray-600">{{ review.content }}</p>
            <div v-if="review.images" class="flex gap-2 mt-2">
              <img v-for="(img, i) in review.images" :key="i" :src="img" class="w-20 h-20 object-cover rounded" />
            </div>
          </div>
        </div>
      </div>
    </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { get } from '~/utils/request'
import { useCartStore } from '~/stores/cart'

const route = useRoute()
const cartStore = useCartStore()
const productId = computed(() => Number(route.params.id))

const currentImageIndex = ref(0)
const quantity = ref(1)
const activeTab = ref('detail')
const selectedSpecs = ref<Record<string, string>>({})
const matchedSkuId = ref<number>(0)
const loading = ref(true)

const detailTabs = [
  { label: '商品详情', value: 'detail' },
  { label: '用户评价', value: 'reviews' },
]

const product = reactive({
  id: 0,
  title: '',
  subtitle: '',
  price: 0,
  originalPrice: 0,
  stock: 0,
  salesCount: 0,
  reviewCount: 0,
  rating: 0,
  shopName: '',
  images: [] as string[],
  description: '',
  specs: {} as Record<string, string>,
})

const specGroups = ref<any[]>([])
const skuList = ref<any[]>([])
const reviews = ref<any[]>([])

// 为规格选择器转换 SKU 列表格式（解析 specData）
const skuListForSelector = computed(() => {
  return skuList.value.map((sku: any) => ({
    id: sku.id,
    specData: typeof sku.specData === 'string' ? JSON.parse(sku.specData) : sku.specData
  }))
})

// 从接口加载商品详情
const fetchProduct = async () => {
  try {
    loading.value = true
    const data: any = await get(`/product/${productId.value}`)
    if (!data) return

    product.id = data.id
    product.title = data.name || ''
    product.subtitle = data.subtitle || ''
    product.price = data.minPrice || 0
    product.originalPrice = data.minPrice ? Math.round(data.minPrice * 1.2 * 100) / 100 : 0
    product.salesCount = data.salesCount || 0
    product.reviewCount = 0
    product.rating = 0
    product.shopName = ''
    product.description = data.detailHtml || ''

    // 图片列表
    const imgs: string[] = []
    if (data.mainImage) imgs.push(data.mainImage)
    if (data.subImages) {
      data.subImages.split(',').forEach((url: string) => {
        if (url.trim()) imgs.push(url.trim())
      })
    }
    product.images = imgs.length ? imgs : ['https://via.placeholder.com/600x600?text=暂无图片']

    // 从 SKU 构建规格组和 SKU 列表
    skuList.value = data.skus || []
    const groupMap: Record<string, Set<string>> = {}
    for (const sku of skuList.value) {
      if (sku.specData) {
        const specData = typeof sku.specData === 'string' ? JSON.parse(sku.specData) : sku.specData
        for (const [key, val] of Object.entries(specData)) {
          if (!groupMap[key]) groupMap[key] = new Set()
          groupMap[key].add(val as string)
        }
      }
    }
    specGroups.value = Object.entries(groupMap).map(([name, options]) => ({
      name,
      options: Array.from(options),
    }))

    // 无规格商品默认选中第一个 SKU
    if (specGroups.value.length === 0 && skuList.value.length > 0) {
      matchedSkuId.value = skuList.value[0].id
    }

    // 规格参数展示
    const specs: Record<string, string> = {}
    if (specGroups.value.length > 0 && skuList.value.length > 0) {
      const firstSku = skuList.value[0]
      const specData = typeof firstSku.specData === 'string' ? JSON.parse(firstSku.specData) : firstSku.specData
      for (const [key, val] of Object.entries(specData)) {
        specs[key] = val as string
      }
    }
    product.specs = specs
  } catch (e) {
    console.error('获取商品详情失败:', e)
  } finally {
    loading.value = false
  }
}

function onSpecChange(specs: Record<string, string>) {
  selectedSpecs.value = specs
  // 根据选中规格匹配 SKU，更新价格、库存和 SKU ID
  const matched = skuList.value.find((sku: any) => {
    const specData = typeof sku.specData === 'string' ? JSON.parse(sku.specData) : sku.specData
    return Object.entries(specs).every(([k, v]) => specData[k] === v)
  })
  if (matched) {
    matchedSkuId.value = matched.id
    product.price = matched.price || product.price
    product.stock = matched.stock || 0
  }
}

// 数量边界约束
function clampQuantity() {
  if (quantity.value < 1) quantity.value = 1
  if (product.stock > 0 && quantity.value > product.stock) quantity.value = product.stock
}

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

async function handleAddToCart() {
  if (specGroups.value.length > 0 && Object.keys(selectedSpecs.value).length < specGroups.value.length) {
    const missingGroups = specGroups.value.filter((g: any) => !selectedSpecs.value[g.name])
    showToast(`请选择${missingGroups.map((g: any) => g.name).join('、')}`, 'error')
    return
  }
  if (product.stock > 0 && quantity.value > product.stock) {
    showToast(`库存不足，当前库存 ${product.stock} 件`, 'error')
    quantity.value = product.stock
    return
  }
  try {
    await cartStore.addToCart(matchedSkuId.value, quantity.value)
    showToast('已添加到购物车！', 'success')
  } catch (error: any) {
    showToast(error?.message || '添加失败，请重试', 'error')
  }
}

function handleBuyNow() {
  handleAddToCart().then(async () => {
    if (toast.type === 'success') {
      // 将刚加入的商品设为选中状态
      const addedItem = cartStore.items.find(i => i.skuId === matchedSkuId.value)
      if (addedItem && !addedItem.checked) {
        cartStore.toggleItem(addedItem.cartId)
      }
      setTimeout(() => navigateTo('/checkout'), 800)
    }
  })
}

// 路由参数变化时重新加载（SPA 内切换商品）
watch(() => route.params.id, () => {
  currentImageIndex.value = 0
  quantity.value = 1
  selectedSpecs.value = {}
  matchedSkuId.value = 0
  fetchProduct()
})

onMounted(() => {
  fetchProduct()
})
</script>

<style scoped>
.toast-enter-active,
.toast-leave-active {
  transition: all 0.3s ease;
}
.toast-enter-from,
.toast-leave-to {
  opacity: 0;
  transform: translate(-50%, -10px);
}
.toast-enter-to,
.toast-leave-from {
  opacity: 1;
  transform: translate(-50%, 0);
}
</style>
