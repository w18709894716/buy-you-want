<template>
  <div>
    <!-- 轮播 Banner -->
    <section class="max-w-7xl mx-auto px-4 mt-4">
      <div class="relative rounded-lg overflow-hidden bg-gradient-to-r from-primary-500 to-primary-700 h-40 sm:h-64 flex items-center">
        <div class="text-white px-6 sm:px-12">
          <h2 class="text-2xl sm:text-4xl font-bold mb-2 sm:mb-4">买你所想，尽在此刻</h2>
          <p class="text-sm sm:text-lg opacity-90 mb-3 sm:mb-6">全场满减优惠，新品上市特惠</p>
          <button class="bg-white text-primary px-5 sm:px-8 py-2 rounded-full font-medium hover:bg-gray-100 transition-colors text-sm sm:text-base">
            立即抢购
          </button>
        </div>
        <!-- Banner 指示器 -->
        <div class="absolute bottom-4 left-1/2 -translate-x-1/2 flex gap-2">
          <span class="w-8 h-1.5 bg-white rounded-full"></span>
          <span class="w-8 h-1.5 bg-white/50 rounded-full"></span>
          <span class="w-8 h-1.5 bg-white/50 rounded-full"></span>
        </div>
      </div>
    </section>

    <!-- 三级分类导航 -->
    <section class="max-w-7xl mx-auto px-4 mt-6">
      <div class="bg-white rounded-lg p-6">
        <div class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-5 gap-4 sm:gap-6">
          <div
            v-for="category in categoryTree"
            :key="category.name"
            class="group relative"
            @mouseenter="hoveredCategory = category.name"
            @mouseleave="hoveredCategory = ''"
          >
            <div class="flex items-center gap-2 cursor-pointer text-gray-700 hover:text-primary transition-colors">
              <span class="text-2xl">{{ category.icon }}</span>
              <span class="font-medium">{{ category.name }}</span>
            </div>
            <!-- 二级分类 -->
            <div class="mt-2 flex flex-wrap gap-2">
              <NuxtLink
                v-for="sub in category.children"
                :key="sub.name"
                :to="`/search?category=${sub.name}`"
                class="text-xs text-gray-500 hover:text-primary"
              >
                {{ sub.name }}
              </NuxtLink>
            </div>
            <!-- 三级分类弹出 -->
            <div
              v-if="hoveredCategory === category.name"
              class="absolute left-0 top-full mt-1 z-40 bg-white rounded-lg shadow-xl border p-4 w-64 hidden group-hover:block"
            >
              <div v-for="sub in category.children" :key="sub.name" class="mb-3">
                <div class="text-sm font-medium text-gray-800 mb-1">{{ sub.name }}</div>
                <div class="flex flex-wrap gap-1">
                  <NuxtLink
                    v-for="third in sub.children"
                    :key="third"
                    :to="`/search?category=${third}`"
                    class="text-xs text-gray-500 hover:text-primary bg-gray-50 px-2 py-0.5 rounded"
                  >
                    {{ third }}
                  </NuxtLink>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- 快捷入口 -->
    <section class="max-w-7xl mx-auto px-4 mt-6">
      <div class="grid grid-cols-5 sm:grid-cols-10 gap-3">
        <div
          v-for="shortcut in shortcuts"
          :key="shortcut.label"
          class="flex flex-col items-center gap-1 py-3 bg-white rounded-lg hover:shadow-md transition-shadow cursor-pointer"
        >
          <span class="text-2xl">{{ shortcut.icon }}</span>
          <span class="text-xs text-gray-600">{{ shortcut.label }}</span>
        </div>
      </div>
    </section>

    <!-- 热门商品 -->
    <section class="max-w-7xl mx-auto px-4 mt-8">
      <div class="flex items-center justify-between mb-4">
        <h2 class="text-xl font-bold text-gray-800">热门商品</h2>
        <NuxtLink to="/search?sort=hot" class="text-sm text-primary hover:text-primary-600">
          查看更多 &gt;
        </NuxtLink>
      </div>
      <div class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-4">
        <ProductCard v-for="product in hotProducts" :key="product.id" :product="product" />
      </div>
    </section>

    <!-- 新品推荐 -->
    <section class="max-w-7xl mx-auto px-4 mt-10">
      <div class="flex items-center justify-between mb-4">
        <h2 class="text-xl font-bold text-gray-800">新品推荐</h2>
        <NuxtLink to="/search?sort=new" class="text-sm text-primary hover:text-primary-600">
          查看更多 &gt;
        </NuxtLink>
      </div>
      <div class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-4">
        <ProductCard v-for="product in newProducts" :key="product.id" :product="product" />
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { get } from '~/utils/request'

const hoveredCategory = ref('')

// 分类数据从接口获取
const categoryTree = ref<any[]>([])

const fetchCategories = async () => {
  try {
    const data = await get('/product/category/tree')
    categoryTree.value = data || []
  } catch (e) {
    console.error('获取分类失败:', e)
  }
}

// 快捷入口
const shortcuts = [
  { icon: '⚡', label: '限时秒杀' },
  { icon: '🎁', label: '新人专享' },
  { icon: '🏷️', label: '品牌特卖' },
  { icon: '💰', label: '领券中心' },
  { icon: '🔥', label: '热卖榜' },
  { icon: '✨', label: '新品首发' },
  { icon: '🌍', label: '海外精选' },
  { icon: '🎮', label: '充值中心' },
  { icon: '📦', label: '物流查询' },
  { icon: '💬', label: '在线客服' },
]

// 热门商品从接口获取
const hotProducts = ref<any[]>([])
const newProducts = ref<any[]>([])

const fetchProducts = async () => {
  try {
    // 获取热门商品（按销量排序）
    const hotData = await get('/product/list', { pageNum: 1, pageSize: 10, sort: 'sales' })
    hotProducts.value = (hotData?.list || []).map((p: any) => ({
      id: p.id,
      title: p.name,
      image: p.mainImage,
      price: p.price || p.minPrice,
      originalPrice: p.originalPrice,
      salesCount: p.salesCount,
      promotion: p.promotion
    }))
  } catch (e) {
    console.error('获取热门商品失败:', e)
  }

  try {
    // 获取新品（按创建时间排序）
    const newData = await get('/product/list', { pageNum: 1, pageSize: 5, sort: 'new' })
    newProducts.value = (newData?.list || []).map((p: any) => ({
      id: p.id,
      title: p.name,
      image: p.mainImage,
      price: p.price || p.minPrice,
      originalPrice: p.originalPrice,
      salesCount: p.salesCount,
      promotion: p.promotion
    }))
  } catch (e) {
    console.error('获取新品失败:', e)
  }
}

onMounted(() => {
  fetchCategories()
  fetchProducts()
})
</script>
