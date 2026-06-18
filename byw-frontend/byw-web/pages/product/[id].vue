<template>
  <div class="max-w-7xl mx-auto px-4 py-6">
    <!-- 面包屑 -->
    <nav class="text-sm text-gray-500 mb-4">
      <NuxtLink to="/" class="hover:text-primary">首页</NuxtLink>
      <span class="mx-2">/</span>
      <NuxtLink to="/search" class="hover:text-primary">全部商品</NuxtLink>
      <span class="mx-2">/</span>
      <span class="text-gray-800">{{ product.title }}</span>
    </nav>

    <!-- 商品详情主体 -->
    <div class="bg-white rounded-lg p-6">
      <div class="flex gap-8">
        <!-- 左侧图片画廊 -->
        <div class="w-96 flex-shrink-0">
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
              <span class="text-3xl font-bold text-primary">¥{{ product.price.toFixed(2) }}</span>
              <span class="text-gray-400 line-through text-sm">¥{{ product.originalPrice.toFixed(2) }}</span>
            </div>
            <div class="mt-2 flex gap-2">
              <span class="text-xs bg-primary text-white px-2 py-0.5 rounded">满减</span>
              <span class="text-xs bg-primary text-white px-2 py-0.5 rounded">包邮</span>
              <span class="text-xs bg-primary text-white px-2 py-0.5 rounded">7天无理由</span>
            </div>
          </div>

          <!-- 销量信息 -->
          <div class="flex gap-6 mt-4 text-sm text-gray-500">
            <span>累计销量：<span class="text-gray-800">{{ product.salesCount }}</span></span>
            <span>累计评价：<span class="text-gray-800">{{ product.reviewCount }}</span></span>
            <span>店铺：<span class="text-primary">{{ product.shopName }}</span></span>
          </div>

          <!-- SKU 选择器 -->
          <div class="mt-6">
            <SkuSelector :spec-groups="specGroups" @change="onSpecChange" />
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
                max="999"
                class="w-14 h-8 text-center border-x outline-none text-sm"
              />
              <button
                class="w-8 h-8 flex items-center justify-center hover:bg-gray-100"
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
            'px-8 py-3 text-sm font-medium border-b-2 transition-colors',
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
          <div class="mt-6 grid grid-cols-2 gap-4">
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
  </div>
</template>

<script setup lang="ts">
import { useCartStore } from '~/stores/cart'

const route = useRoute()
const cartStore = useCartStore()
const productId = computed(() => Number(route.params.id))

const currentImageIndex = ref(0)
const quantity = ref(1)
const activeTab = ref('detail')
const skuSelectorRef = ref()
const selectedSpecs = ref<Record<string, string>>({})

const detailTabs = [
  { label: '商品详情', value: 'detail' },
  { label: '用户评价', value: 'reviews' },
]

// 占位商品数据
const product = reactive({
  id: productId.value,
  title: 'Apple iPhone 15 Pro Max 256GB 原色钛金属 A17 Pro芯片 4800万像素主摄',
  subtitle: '【限时优惠】钛金属设计 | A17 Pro芯片 | 4800万像素专业级相机系统',
  price: 9999,
  originalPrice: 10999,
  stock: 256,
  salesCount: 58234,
  reviewCount: 12860,
  rating: 4.8,
  shopName: 'Apple官方旗舰店',
  images: [
    'https://via.placeholder.com/600x600?text=iPhone+15+Pro+Max+1',
    'https://via.placeholder.com/600x600?text=iPhone+15+Pro+Max+2',
    'https://via.placeholder.com/600x600?text=iPhone+15+Pro+Max+3',
    'https://via.placeholder.com/600x600?text=iPhone+15+Pro+Max+4',
  ],
  description: 'iPhone 15 Pro Max。采用航空级钛金属设计，搭载 A17 Pro 芯片，配备 4800 万像素主摄系统和 5 倍光学变焦长焦镜头。支持 USB 3 高速传输，Action Button 自定义按键，全天候电池续航。6.7 英寸超视网膜 XDR 显示屏，ProMotion 自适应刷新率技术，最高 2000 尼特峰值亮度。',
  specs: {
    '品牌': 'Apple',
    '型号': 'iPhone 15 Pro Max',
    '存储容量': '256GB',
    '屏幕尺寸': '6.7英寸',
    '芯片': 'A17 Pro',
    '主摄像头': '4800万像素',
    '电池类型': '不可拆卸式锂离子电池',
    '网络类型': '5G全网通',
    '操作系统': 'iOS 17',
    '重量': '221g',
  },
})

const specGroups = [
  { name: '颜色', options: ['原色钛金属', '蓝色钛金属', '白色钛金属', '黑色钛金属'] },
  { name: '存储容量', options: ['256GB', '512GB', '1TB'] },
]

const reviews = [
  { id: 1, username: '张***明', date: '2026-06-10', rating: 5, content: '手机质量非常好，拍照效果惊艳！钛金属手感一流，电池续航也很给力。物流速度很快，第二天就到了。', images: ['https://via.placeholder.com/100x100?text=评价1', 'https://via.placeholder.com/100x100?text=评价2'] },
  { id: 2, username: '李***华', date: '2026-06-08', rating: 5, content: '从14 Pro升级过来，提升明显。A17 Pro芯片性能强劲，玩原神完全无压力。5倍光学变焦拍远处的景物非常清晰。' },
  { id: 3, username: '王***', date: '2026-06-05', rating: 4, content: '整体不错，就是价格有点贵。钛金属质感确实好，比不锈钢轻了不少。Action Button很方便，设置成静音开关。' },
]

function onSpecChange(specs: Record<string, string>) {
  selectedSpecs.value = specs
}

async function handleAddToCart() {
  const specsStr = Object.entries(selectedSpecs.value).map(([k, v]) => `${k}:${v}`).join(' ')
  await cartStore.addToCart(productId.value, quantity.value, specsStr)
  // 提示添加成功
  alert('已添加到购物车！')
}

function handleBuyNow() {
  handleAddToCart().then(() => navigateTo('/cart'))
}
</script>
