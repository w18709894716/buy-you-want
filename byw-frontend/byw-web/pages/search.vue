<template>
  <div class="max-w-7xl mx-auto px-4 py-6">
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
  </div>
</template>

<script setup lang="ts">
import { get } from '~/utils/request'

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

onMounted(() => {
  fetchCategories()
  fetchProducts()
})

// 路由查询参数变化时重新查询（分类切换、关键词变化等）
watch(() => route.query.category, () => {
  currentPage.value = 1
  fetchProducts()
})
</script>
