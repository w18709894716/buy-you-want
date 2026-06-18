<template>
  <div class="max-w-7xl mx-auto px-4 py-6">
    <!-- 搜索头部 -->
    <div class="mb-6">
      <SearchBar />
    </div>

    <div class="flex gap-6">
      <!-- 左侧筛选 -->
      <aside class="w-56 flex-shrink-0 hidden lg:block">
        <div class="bg-white rounded-lg p-4 sticky top-40">
          <!-- 价格区间 -->
          <h3 class="font-medium text-gray-800 mb-3">价格区间</h3>
          <div class="flex items-center gap-2 mb-4">
            <input
              v-model.number="priceRange.min"
              type="number"
              placeholder="最低"
              class="w-full h-8 px-2 border border-gray-300 rounded text-sm outline-none focus:border-primary"
            />
            <span class="text-gray-400">-</span>
            <input
              v-model.number="priceRange.max"
              type="number"
              placeholder="最高"
              class="w-full h-8 px-2 border border-gray-300 rounded text-sm outline-none focus:border-primary"
            />
          </div>
          <div class="flex flex-wrap gap-2 mb-6">
            <button
              v-for="range in priceRanges"
              :key="range.label"
              class="text-xs px-3 py-1 border rounded hover:border-primary hover:text-primary transition-colors"
              :class="activePriceRange === range.label ? 'border-primary text-primary bg-primary-50' : 'border-gray-200 text-gray-600'"
              @click="selectPriceRange(range)"
            >
              {{ range.label }}
            </button>
          </div>

          <!-- 分类筛选 -->
          <h3 class="font-medium text-gray-800 mb-3">商品分类</h3>
          <div class="space-y-2">
            <NuxtLink
              v-for="cat in filterCategories"
              :key="cat"
              :to="`/search?keyword=${keyword}&category=${cat}`"
              class="block text-sm text-gray-600 hover:text-primary py-1"
              :class="{ 'text-primary font-medium': category === cat }"
            >
              {{ cat }}
            </NuxtLink>
          </div>
        </div>
      </aside>

      <!-- 右侧商品列表 -->
      <div class="flex-1">
        <!-- 排序栏 -->
        <div class="bg-white rounded-lg p-3 mb-4 flex items-center gap-4 text-sm">
          <button
            v-for="sort in sortOptions"
            :key="sort.value"
            :class="[
              'px-3 py-1.5 rounded transition-colors',
              currentSort === sort.value ? 'bg-primary text-white' : 'text-gray-600 hover:bg-gray-100'
            ]"
            @click="changeSort(sort.value)"
          >
            {{ sort.label }}
          </button>
          <div class="flex-1"></div>
          <span class="text-gray-400 text-xs">共 {{ total }} 件商品</span>
        </div>

        <!-- 商品网格 -->
        <div v-if="products.length > 0" class="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-4">
          <ProductCard v-for="product in products" :key="product.id" :product="product" />
        </div>

        <!-- 空状态 -->
        <div v-else class="bg-white rounded-lg p-16 text-center">
          <div class="text-6xl mb-4">🔍</div>
          <p class="text-gray-400">没有找到相关商品，换个关键词试试吧</p>
        </div>

        <!-- 分页 -->
        <div v-if="totalPages > 1" class="mt-6 flex justify-center gap-2">
          <button
            :disabled="currentPage <= 1"
            class="px-4 py-2 border rounded text-sm disabled:opacity-50 hover:border-primary hover:text-primary transition-colors"
            @click="goPage(currentPage - 1)"
          >
            上一页
          </button>
          <button
            v-for="page in visiblePages"
            :key="page"
            :class="[
              'px-4 py-2 border rounded text-sm transition-colors',
              page === currentPage ? 'bg-primary text-white border-primary' : 'hover:border-primary hover:text-primary'
            ]"
            @click="goPage(page)"
          >
            {{ page }}
          </button>
          <button
            :disabled="currentPage >= totalPages"
            class="px-4 py-2 border rounded text-sm disabled:opacity-50 hover:border-primary hover:text-primary transition-colors"
            @click="goPage(currentPage + 1)"
          >
            下一页
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
const route = useRoute()

const keyword = computed(() => (route.query.keyword as string) || '')
const category = computed(() => (route.query.category as string) || '')
const currentSort = ref('default')
const currentPage = ref(1)
const pageSize = 20
const total = ref(0)

const priceRange = reactive({ min: 0, max: 0 })
const activePriceRange = ref('')

const sortOptions = [
  { label: '综合排序', value: 'default' },
  { label: '销量优先', value: 'sales' },
  { label: '价格升序', value: 'price_asc' },
  { label: '价格降序', value: 'price_desc' },
  { label: '新品优先', value: 'new' },
]

const priceRanges = [
  { label: '0-100', min: 0, max: 100 },
  { label: '100-500', min: 100, max: 500 },
  { label: '500-1000', min: 500, max: 1000 },
  { label: '1000-5000', min: 1000, max: 5000 },
  { label: '5000+', min: 5000, max: 0 },
]

const filterCategories = ['数码电器', '服装鞋包', '食品生鲜', '美妆护肤', '家居家装', '运动户外', '图书文具']

// 占位商品数据
const products = ref([
  { id: 1, title: 'Apple iPhone 15 Pro Max 256GB 原色钛金属', image: 'https://via.placeholder.com/300x300?text=iPhone+15', price: 9999, originalPrice: 10999, salesCount: 58000, promotion: '热卖' },
  { id: 2, title: '华为 Mate 60 Pro 512GB 雅丹黑', image: 'https://via.placeholder.com/300x300?text=Mate+60', price: 7999, originalPrice: 8999, salesCount: 42000, promotion: '新品' },
  { id: 3, title: '小米14 Ultra 影像旗舰 16+512GB', image: 'https://via.placeholder.com/300x300?text=Mi+14', price: 6499, salesCount: 31000 },
  { id: 4, title: 'Sony WH-1000XM5 无线降噪头戴式耳机', image: 'https://via.placeholder.com/300x300?text=Sony+XM5', price: 2299, originalPrice: 2999, salesCount: 18000, promotion: '特价' },
  { id: 5, title: 'Nintendo Switch OLED 马力欧限定版', image: 'https://via.placeholder.com/300x300?text=Switch', price: 2349, salesCount: 25000 },
  { id: 6, title: '戴森 V15 Detect 无线吸尘器', image: 'https://via.placeholder.com/300x300?text=Dyson+V15', price: 4990, originalPrice: 5990, salesCount: 12000, promotion: '满减' },
  { id: 7, title: 'LEGO 乐高 42151 布加迪 Bolide', image: 'https://via.placeholder.com/300x300?text=LEGO', price: 399, salesCount: 8900 },
  { id: 8, title: 'Apple MacBook Air M3 15英寸 16+512GB', image: 'https://via.placeholder.com/300x300?text=MacBook', price: 12499, originalPrice: 13499, salesCount: 9800, promotion: '教育优惠' },
])

total.value = 86
const totalPages = computed(() => Math.ceil(total.value / pageSize))

const visiblePages = computed(() => {
  const pages: number[] = []
  const start = Math.max(1, currentPage.value - 2)
  const end = Math.min(totalPages.value, currentPage.value + 2)
  for (let i = start; i <= end; i++) pages.push(i)
  return pages
})

function changeSort(sort: string) {
  currentSort.value = sort
  currentPage.value = 1
  // TODO: 调用接口重新加载数据
}

function goPage(page: number) {
  currentPage.value = page
  // TODO: 调用接口加载对应页数据
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function selectPriceRange(range: { label: string; min: number; max: number }) {
  activePriceRange.value = range.label
  priceRange.min = range.min
  priceRange.max = range.max
  currentPage.value = 1
}
</script>
