<template>
  <div class="max-w-7xl mx-auto px-4 py-6">
    <!-- 搜索范围 Tab（与顶部搜索框下拉同步）：宝贝 / 店铺 -->
    <div class="bg-white rounded-lg p-2 mb-4 flex items-center gap-2 text-sm">
      <button
        :class="[
          'px-4 py-1.5 rounded transition-colors',
          scope === 'product' ? 'bg-primary text-white' : 'text-gray-600 hover:bg-gray-100'
        ]"
        @click="switchScope('product')"
      >宝贝</button>
      <button
        :class="[
          'px-4 py-1.5 rounded transition-colors',
          scope === 'shop' ? 'bg-primary text-white' : 'text-gray-600 hover:bg-gray-100'
        ]"
        @click="switchScope('shop')"
      >店铺</button>
    </div>

    <div v-if="scope === 'product'" class="flex gap-6">
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
          <div class="space-y-1">
            <template v-for="cat in filterCategories" :key="cat.id">
              <NuxtLink
                :to="`/search?keyword=${keyword}&category=${cat.name}`"
                class="block text-sm text-gray-600 hover:text-primary py-1"
                :class="{ 'text-primary font-medium': category === cat.name }"
              >
                {{ cat.name }}
              </NuxtLink>
              <template v-if="cat.children && cat.children.length">
                <template v-for="sub in cat.children" :key="sub.id">
                  <NuxtLink
                    :to="`/search?keyword=${keyword}&category=${sub.name}`"
                    class="block text-sm text-gray-500 hover:text-primary py-0.5 pl-4"
                    :class="{ 'text-primary font-medium': category === sub.name }"
                  >
                    {{ sub.name }}
                  </NuxtLink>
                  <NuxtLink
                    v-for="third in sub.children"
                    :key="third.id"
                    :to="`/search?keyword=${keyword}&category=${third.name}`"
                    class="block text-xs text-gray-400 hover:text-primary py-0.5 pl-8"
                    :class="{ 'text-primary font-medium': category === third.name }"
                  >
                    {{ third.name }}
                  </NuxtLink>
                </template>
              </template>
            </template>
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

    <!-- 店铺搜索结果（淘宝式店铺卡片） -->
    <div v-else>
      <div v-if="shops.length > 0" class="space-y-3">
        <div v-for="s in shops" :key="s.id" class="bg-white rounded-lg p-4 flex items-center gap-4">
          <img v-if="s.logo" :src="s.logo" alt="" class="w-14 h-14 rounded-full object-cover flex-shrink-0" />
          <div v-else class="w-14 h-14 rounded-full bg-primary-50 text-primary flex items-center justify-center text-2xl flex-shrink-0">🏬</div>
          <div class="flex-1 min-w-0">
            <div class="flex items-center gap-2">
              <NuxtLink :to="`/shop/${s.id}`" class="font-medium text-gray-800 hover:text-primary transition-colors">{{ s.name }}</NuxtLink>
              <span v-if="s.selfOperated === 0" class="text-xs px-1.5 py-0.5 bg-primary text-white rounded flex-shrink-0">自营</span>
            </div>
            <p class="text-xs text-gray-400 mt-1 truncate">{{ s.description || '这家店主很懒，暂时没有介绍' }}</p>
          </div>
          <NuxtLink
            :to="`/shop/${s.id}`"
            class="flex-shrink-0 h-8 px-5 border border-primary text-primary rounded-full text-sm flex items-center hover:bg-primary hover:text-white transition-colors"
          >进店</NuxtLink>
        </div>
      </div>

      <!-- 空状态 -->
      <div v-else class="bg-white rounded-lg p-16 text-center">
        <div class="text-6xl mb-4">🏬</div>
        <p class="text-gray-400">没有找到相关店铺，换个关键词试试吧</p>
      </div>

      <div class="mt-4 text-right text-gray-400 text-xs">共 {{ shopTotal }} 家店铺</div>

      <!-- 店铺分页 -->
      <div v-if="shopTotalPages > 1" class="mt-4 flex justify-center gap-2">
        <button
          :disabled="shopPage <= 1"
          class="px-4 py-2 border rounded text-sm disabled:opacity-50 hover:border-primary hover:text-primary transition-colors"
          @click="goShopPage(shopPage - 1)"
        >
          上一页
        </button>
        <button
          v-for="page in shopVisiblePages"
          :key="page"
          :class="[
            'px-4 py-2 border rounded text-sm transition-colors',
            page === shopPage ? 'bg-primary text-white border-primary' : 'hover:border-primary hover:text-primary'
          ]"
          @click="goShopPage(page)"
        >
          {{ page }}
        </button>
        <button
          :disabled="shopPage >= shopTotalPages"
          class="px-4 py-2 border rounded text-sm disabled:opacity-50 hover:border-primary hover:text-primary transition-colors"
          @click="goShopPage(shopPage + 1)"
        >
          下一页
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { get } from '~/utils/request'

const route = useRoute()

const keyword = computed(() => (route.query.keyword as string) || '')
const category = computed(() => (route.query.category as string) || '')
/** 搜索范围：product-宝贝 shop-店铺（URL scope 参数驱动，与顶部搜索框下拉同步） */
const scope = computed<'product' | 'shop'>(() => (route.query.scope as string) === 'shop' ? 'shop' : 'product')
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

const filterCategories = ref<any[]>([])

// 商品列表从接口获取
const products = ref<any[]>([])

const totalPages = computed(() => Math.ceil(total.value / pageSize))

const visiblePages = computed(() => {
  const pages: number[] = []
  const start = Math.max(1, currentPage.value - 2)
  const end = Math.min(totalPages.value, currentPage.value + 2)
  for (let i = start; i <= end; i++) pages.push(i)
  return pages
})

const fetchProducts = async () => {
  try {
    const data = await get('/product/list', {
      pageNum: currentPage.value,
      pageSize,
      keyword: keyword.value || undefined,
      category: category.value || undefined,
      sort: currentSort.value,
      minPrice: priceRange.min || undefined,
      maxPrice: priceRange.max || undefined
    })
    products.value = (data?.list || []).map((p: any) => ({
      id: p.id,
      title: p.name,
      image: p.mainImage,
      price: p.price || p.minPrice,
      originalPrice: p.originalPrice,
      salesCount: p.salesCount,
      promotion: p.promotion
    }))
    total.value = data?.total || 0
  } catch (e) {
    console.error('获取商品列表失败:', e)
    products.value = []
    total.value = 0
  }
}

const fetchCategories = async () => {
  try {
    const data = await get('/product/category/tree')
    filterCategories.value = data || []
  } catch (e) {
    console.error('获取分类失败:', e)
  }
}

function changeSort(sort: string) {
  currentSort.value = sort
  currentPage.value = 1
  fetchProducts()
}

function goPage(page: number) {
  currentPage.value = page
  fetchProducts()
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function selectPriceRange(range: { label: string; min: number; max: number }) {
  if (activePriceRange.value === range.label) {
    // 再次点击已选中区间 → 取消价格筛选
    activePriceRange.value = ''
    priceRange.min = 0
    priceRange.max = 0
  } else {
    activePriceRange.value = range.label
    priceRange.min = range.min
    priceRange.max = range.max
  }
  currentPage.value = 1
  fetchProducts()
}

const productLoaded = ref(false)

onMounted(() => {
  if (scope.value === 'shop') {
    fetchShops()
  } else {
    productLoaded.value = true
    fetchCategories()
    fetchProducts()
  }
})

// 路由查询参数变化时重新查询（分类切换等）
watch(() => route.query.category, () => {
  currentPage.value = 1
  fetchProducts()
})

// 关键词变化时按当前范围重新查询（顶部搜索框在搜索页内再次搜索）
watch(() => route.query.keyword, () => {
  currentPage.value = 1
  shopPage.value = 1
  if (scope.value === 'shop') fetchShops()
  else fetchProducts()
})

// 范围切换（Tab 或顶部下拉）时拉取店铺结果；首次切回宝贝时懒加载商品列表
watch(scope, (val) => {
  if (val === 'shop') {
    shopPage.value = 1
    fetchShops()
  } else if (!productLoaded.value) {
    productLoaded.value = true
    fetchCategories()
    fetchProducts()
  }
})

// ========== 店铺搜索 ==========
const shops = ref<any[]>([])
const shopTotal = ref(0)
const shopPage = ref(1)
const shopPageSize = 10
const shopTotalPages = computed(() => Math.ceil(shopTotal.value / shopPageSize))
const shopVisiblePages = computed(() => {
  const pages: number[] = []
  const start = Math.max(1, shopPage.value - 2)
  const end = Math.min(shopTotalPages.value, shopPage.value + 2)
  for (let i = start; i <= end; i++) pages.push(i)
  return pages
})

const fetchShops = async () => {
  try {
    const data = await get('/shop/search', {
      keyword: keyword.value || undefined,
      pageNum: shopPage.value,
      pageSize: shopPageSize
    })
    shops.value = data?.list || []
    shopTotal.value = data?.total || 0
  } catch (e) {
    console.error('获取店铺搜索结果失败:', e)
    shops.value = []
    shopTotal.value = 0
  }
}

function goShopPage(page: number) {
  shopPage.value = page
  fetchShops()
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function switchScope(s: 'product' | 'shop') {
  if (s === scope.value) return
  navigateTo({
    path: '/search',
    query: { keyword: keyword.value || undefined, scope: s === 'shop' ? 'shop' : undefined },
  })
}
</script>
